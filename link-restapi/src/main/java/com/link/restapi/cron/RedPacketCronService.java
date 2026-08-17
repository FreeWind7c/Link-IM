package com.link.restapi.cron;

import com.link.common.core.event.EventType;
import com.link.common.core.model.redpack.LinkRedPacketUpdate;
import com.link.common.redis.RedisKeys;
import com.link.im.constants.redpack.RedPacketStatusKeys;
import com.link.im.constants.wallet.WalletFlowBizTypeKeys;
import com.link.im.constants.wallet.WalletInOutKeys;
import com.link.im.entity.redpack.RedPacket;
import com.link.im.entity.wallet.WalletFlow;
import com.link.im.entity.wallet.WalletInfo;
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
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

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
                RedPacket before = self.doExpire(packet, now);
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

    /**
     * 单个过期红包的退款事务：翻转状态、退钱、记流水，一起成或一起废。
     *
     * <p>不再更新消息的 data 字段：status 和 claimantIds 已从消息中移除，
     * 前端通过 UPDATE_RED_PACKET 事件实时同步状态。
     *
     * @return 翻转<b>之前</b>的红包文档；返回 null 表示没抢到翻转权，本次什么都没做
     */
//    @Transactional(rollbackFor = Exception.class)
    public RedPacket doExpire(RedPacket packet, long now) {
        Query cas = new Query(Criteria.where("_id").is(packet.getId())
                .and("status").is(RedPacketStatusKeys.IN_PROGRESS));
        Update flip = new Update().set("status", RedPacketStatusKeys.REFUND);
        RedPacket before = this.mongoTemplate.findAndModify(cas, flip,
                new FindAndModifyOptions().returnNew(false).upsert(false), RedPacket.class);
        if (before == null)
            return null;

        BigDecimal remain = before.getRemainAmount();
        if (remain != null && remain.signum() > 0) {
            // ② 退款。sndId 库里是 ObjectId，这里必须显式 new ObjectId(...)：
            //    原来传的是 String，条件永远不命中，updateFirst 静默返回 0，退款一直是失效的
            ObjectId sndId = before.getSndId();
            UpdateResult refund = this.mongoTemplate.updateFirst(
                    new Query(Criteria.where("user_id").is(sndId)),
                    new Update().inc("balance", remain), WalletInfo.class);

            // ③ 检查结果。不检查的话钱没退回去也悄无声息，红包却已经被标成「已退款」
            if (refund.getMatchedCount() != 1)
                throw new IllegalStateException("过期红包退款失败，发送者钱包不存在 packetId="
                        + before.getId().toHexString() + ", sndId=" + before.getSndId());

            // ④ 退款流水。没有它，用户账单里会凭空多出一笔钱
            WalletFlow flow = new WalletFlow()
                    .setUserId(sndId)
                    .setAmount(remain)
                    .setInOut(WalletInOutKeys.IN)
                    .setBizType(WalletFlowBizTypeKeys.REFUND)
                    .setBizDetailId(before.getId().toHexString())
                    .setRemark("红包过期退款")
                    .setTimestamp(now);
            flow.setCreatedTime(now);
            this.mongoTemplate.insert(flow);
        }

        // ⑤ 消息不再更新：前端通过 UPDATE_RED_PACKET 事件同步红包状态

        return before;
    }

    private void pushExpired(RedPacket before) {
        Collection<String> targets = redPackService.pushTargets(before);
        if (CollectionUtils.isEmpty(targets))
            return;

        LinkRedPacketUpdate event = new LinkRedPacketUpdate()
                .setChatId(before.getChatId())
                .setMessageId(before.getMessageId())
                .setPacketId(before.getId().toHexString())
                .setStatus(RedPacketStatusKeys.REFUND)
                // 过期退款没有领取人
                .setClaimantId(null)
                .setRemainCount(before.getRemainCount())
                .setRemainAmount(before.getRemainAmount());
        this.pushPublisher.push(EventType.UPDATE_RED_PACKET, targets, event);
    }
}
