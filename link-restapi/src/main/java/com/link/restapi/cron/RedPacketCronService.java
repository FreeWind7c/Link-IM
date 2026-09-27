package com.link.restapi.cron;

import com.link.common.core.event.EventType;
import com.link.common.redis.RedisKeys;
import com.link.common.constants.redpack.RedPacketStatusKeys;
import com.link.common.constants.wallet.WalletFlowBizTypeKeys;
import com.link.common.constants.wallet.WalletInOutKeys;
import com.link.base.entity.data.RedPacketData;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.redpack.RedPacket;
import com.link.base.entity.redpack.RedPacketRecord;
import com.link.base.entity.wallet.WalletFlow;
import com.link.base.entity.wallet.WalletInfo;
import com.link.base.vo.base.BaseMessageVO;
import com.link.restapi.module.redpack.service.RedPackService;
import com.link.restapi.push.RemotePushPublisher;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 过期红包退款。
 *
 * <p>原来还有一个 {@code verifyRedPacketRecord}，用来补偿「记录已落库但没打钱」的中间态。
 * 现在抢红包的扣库存 / 记记录 / 加钱 / 记流水在同一个事务里，这个中间态根本不存在，
 * 那个方法已整体删除（它调的 {@code byTrx(record, null)} 还必然 NPE）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Slf4j
@Component
public class RedPacketCronService {


    private static final int BATCH_LIMIT = 200;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedPackService redPackService;

    @Autowired
    private RemotePushPublisher pushPublisher;

    /** 自注入调事务方法。类内直调走的是原始对象，@Transactional 不生效 */
    @Autowired
    @Lazy
    private RedPacketCronService self;

    /**
     * 扫过期红包，把没抢完的剩余金额原路退回发送者。
     */
    @Scheduled(fixedRate = 10 * 1000)
    public void verifyRedPacket() {
        long now = System.currentTimeMillis();
        // 用属性名而不是库内字段名：RedPacket 的 expireTime/remainCount 上有 @Field 映射，
        // 写属性名 Spring 的 QueryMapper 才会翻译成 expire_time/remain_count，顺带做类型转换
        Query query = new Query(Criteria.where("status").is(RedPacketStatusKeys.IN_PROGRESS)
                .and("expire_time").lte(now)
                .and("remain_count").gt(0));
        query.limit(BATCH_LIMIT);

        List<RedPacket> packets = this.mongoTemplate.find(query, RedPacket.class);
        for (RedPacket packet : packets) {
            try {
                List<RedPacketRecord> records = this.mongoTemplate.find(
                        new Query(Criteria.where("packet_id").is(packet.getId())),
                        RedPacketRecord.class);
                RedPacket before = self.doExpire(packet, records,now);
                if (before == null)
                    continue; // 没抢到翻转权（上一轮或另一个实例已处理），绝不重复退款
                // ---------- 事务外后置：commit 之后再动 Redis 和 MQ ----------
                this.redisTemplate.delete(RedisKeys.RED_PACKET + before.getId().toHexString());
                log.info("红包：{},已退款:{}",before.getId().toHexString(),before.getRemainAmount());
                pushExpired(before);
            } catch (Exception e) {
                // 单个红包失败不能带崩整轮，下一轮还会再扫到它（status 仍是 0）
                log.error("过期红包退款失败 packetId={}, sndId={}", packet.getId(), packet.getSndId(), e);
            }
        }
    }


    @Transactional(rollbackFor = Exception.class)
    public RedPacket doExpire(RedPacket packet, List<RedPacketRecord> records, long now) {
        ObjectId packetId = packet.getId();

        // ① 先翻转状态（抢占处理权）
        Query cas = new Query(Criteria.where("_id").is(packetId)
                .and("status").is(RedPacketStatusKeys.IN_PROGRESS));
        Update flip = new Update().set("status", RedPacketStatusKeys.REFUND);
        RedPacket before = this.mongoTemplate.findAndModify(cas, flip,
                new FindAndModifyOptions().returnNew(false).upsert(false), RedPacket.class);
        if (before == null)
            return null;

        // ② 从 RedPacketRecord 统计实际已抢金额和份数


        BigDecimal grabbedAmount = records.stream()
            .map(RedPacketRecord::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        int grabbedCount = records.size();

        // ③ 计算实际剩余
        BigDecimal actualRemainAmount = before.getTotalAmount().subtract(grabbedAmount);
        int actualRemainCount = before.getTotalCount() - grabbedCount;

        log.info("红包过期退款: packetId={}, total={}, grabbed={}, refund={}",
            packetId, before.getTotalAmount(), grabbedAmount, actualRemainAmount);

        // ④ 退款（如果有剩余）
        if (actualRemainAmount.compareTo(BigDecimal.ZERO) > 0) {
            ObjectId sndId = before.getSndId();
            UpdateResult refund = this.mongoTemplate.updateFirst(
                    new Query(Criteria.where("user_id").is(sndId)),
                    new Update().inc("balance", actualRemainAmount), WalletInfo.class);

            // 检查结果。不检查的话钱没退回去也悄无声息，红包却已经被标成「已退款」
            if (refund.getMatchedCount() != 1)
                throw new IllegalStateException("过期红包退款失败，发送者钱包不存在 packetId="
                        + packetId + ", sndId=" + sndId);

            // ⑤ 退款流水。没有它，用户账单里会凭空多出一笔钱
            WalletFlow flow = new WalletFlow()
                    .setUserId(sndId)
                    .setAmount(actualRemainAmount)
                    .setInOut(WalletInOutKeys.IN)
                    .setBizType(WalletFlowBizTypeKeys.REFUND)
                    .setBizDetailId(packetId.toHexString())
                    .setRemark("红包过期退款")
                    .setTimestamp(now);
            flow.setCreatedTime(now);
            this.mongoTemplate.insert(flow);
        }

        // ⑥ 更新 RedPacket 的最终快照（remain 和 claimantIds）
        List<ObjectId> claimantIds = records.stream()
            .map(RedPacketRecord::getUserId)
            .collect(Collectors.toList());

        this.mongoTemplate.updateFirst(
            new Query(Criteria.where("_id").is(packetId)),
            new Update()
                .set("remain_amount", actualRemainAmount)
                .set("remain_count", actualRemainCount)
                .set("claimant_ids", claimantIds),
            RedPacket.class);

        // ⑦ 构造返回值（用于推送）
        before.setRemainAmount(actualRemainAmount);
        before.setRemainCount(actualRemainCount);
        before.setClaimantIds(claimantIds);

        return before;
    }

    private void pushExpired(RedPacket before) {
        Collection<String> targets = redPackService.pushTargets(before);
        if (CollectionUtils.isEmpty(targets))
            return;

        before.setStatus(RedPacketStatusKeys.REFUND);
        BaseMessageVO vo = new BaseMessageVO();
        Query query = new Query(Criteria.where("packet_id").is(before.getId()));
        query.fields().include("user_id");
        List<RedPacketRecord> records = this.mongoTemplate.find(query, RedPacketRecord.class);
        Set<String> userId = records.stream().map(v -> { return v.getUserId().toHexString();
        }).collect(Collectors.toSet());
        RedPacketData data = RedPacketData.toData(before,userId);
        if (before.getBizType() == 1)
            vo = this.mongoTemplate.findById(before.getMessageId(), DefaultMessageInfo.class).toVo();
        else
            vo = this.mongoTemplate.findById(before.getMessageId(), GroupMessageInfo.class).toVo();
        vo.setData(data.toJson()).setBaseData(data);
        this.pushPublisher.push(EventType.UPDATE_MESSAGE, targets, vo);
    }
}
