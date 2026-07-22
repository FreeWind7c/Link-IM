package com.link.restapi.redpack.service;

import com.link.common.redis.RedisKeys;
import com.link.common.util.id.LinkID;
import com.link.im.entity.redpack.RedPacket;
import com.link.im.entity.redpack.RedPacketItem;
import com.link.im.entity.redpack.RedPacketRecord;
import com.link.im.entity.wallet.WalletFlow;
import com.link.im.entity.wallet.WalletInfo;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.ApiResult;
import com.link.restapi.redpack.model.dto.LinkGrabPacketDto;
import com.link.restapi.redpack.model.dto.LinkSendPacketDto;
import com.mongodb.client.result.UpdateResult;
import io.netty.handler.codec.quic.EpollQuicUtils;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.redisson.api.RedissonClient;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.InvalidMongoDbApiUsageException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Slf4j
@Component
public class RedPackService extends BasePlatFormMongoService<RedPacket> {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private ApplicationContext context;

    @Transactional
    public ApiResult sendRedPacket(LinkSendPacketDto dto) {
        if (!stringValidator(dto.getSndId(),dto.getChatId()) || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0 || dto.getCount() < 1)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        long now = now();
        // 幂等校验
        if(!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(dto.getBizDetailId(),true,30, TimeUnit.SECONDS)))
            return ApiResult.error();

        try{
            // CAS校验
            Query eq = eq(where(
                    col(WalletInfo::getUserId)).is(new ObjectId(dto.getSndId()))
                    .and(col(WalletInfo::getBalance)).gte(dto.getAmount())
            );
            Update inc = update().inc(col(WalletInfo::getBalance), dto.getAmount().negate());
            UpdateResult updateResult = this.getMongoTemplate().updateFirst(eq, inc,WalletInfo.class);

            if (updateResult.getModifiedCount() != 1)
                return ApiResult.error("钱包余额不足");

            // 创建红包
            String redPacketId = LinkID.nextIdStr();
            RedPacket redPacket = createRedPacket(dto,redPacketId, now);
            List<RedPacketItem> items = split(dto,redPacketId, now);
            redPacket.setChildren(items);

            this.insert(redPacket);
            return ApiResult.success().setData(redPacket);
        }catch (Exception e){
            // 抛出异常
            redisTemplate.delete(dto.getBizDetailId());
            throw new RuntimeException("红包创建失败");
        }

    }

    @Transactional
    public ApiResult grabRedPacket(LinkGrabPacketDto dto) {
        // 用分隔符，避免 "12"+"3" 与 "1"+"23" 撞键
        String lockKey = dto.getUserId() + ":" + dto.getPacketId();
        RLock lock = redisson.getLock(lockKey);
        boolean locked = false;
        try {
            // 加锁
            locked = lock.tryLock(30L, TimeUnit.SECONDS);
            if (!locked)
                return ApiResult.error();

            // 幂等性校验：放在碰队列之前，防止重复请求先 pop 掉一份再失败，搅乱池子
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey,true,30L,TimeUnit.SECONDS)))
                return ApiResult.error("请勿重复抢红包");

            long now = now();

            // 从 Redis 预扣队列弹出一份（Redis 权威库存，弹到即预占）
            RedPacketItem item = (RedPacketItem) redisTemplate.opsForList().rightPop(RedisKeys.RED_PACKET + dto.getPacketId());
            if (item == null)
                return ApiResult.success("红包已抢完");

            RedPacketRecord record = new RedPacketRecord().setPacketId(new ObjectId(dto.getPacketId()))
                    .setUserId(new ObjectId(dto.getUserId()))
                    .setChildrenId(item.getId())
                    .setStatus(0)
                    .setDutTime(now + (15 * 1000))
                    .setAmount(item.getAmount());
            try {
                RedPacketRecord packetRecord = this.getMongoTemplate().insert(record);
                record.setId(packetRecord.getId());
            } catch (Exception e) {
                // 写记录失败（如撞 (packetId,userId) 唯一键 = 已抢过），把预占的这份还回队列，然后结束
                redisTemplate.opsForList().rightPush(RedisKeys.RED_PACKET + dto.getPacketId(), item);
                return ApiResult.error("抢红包失败");
            }

            // 记录已落库，再做扣库存+加钱。即便这里崩了，cron 会扫 status=0 的记录补 byTrx，最终一致
            ApiResult result = context.getBean(RedPackService.class).byTrx(record);
            return result.setData(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            if (locked)
                lock.unlock();
        }
    }

    @Transactional
    public ApiResult byTrx(RedPacketRecord record) {
        // CAS抢红包：status==0 是关键，让「抢」和「过期退款」盯同一个字段互斥。
        // cron 一旦把红包翻成 2，这里就匹配不到，过期后残留请求无法再扣，金额才守恒。
        Query eq = eq(
                where(col(RedPacket::getId)).is(record.getPacketId())
                        .and(col(RedPacket::getStatus)).is(0)
                        .and(col(RedPacket::getRemainCount)).gt(0)
                        .and(col(RedPacket::getRemainAmount)).gte(record.getAmount())
                        .and("children").elemMatch(
                                Criteria.where("_id").is(record.getChildrenId())
                                        .and("status").is(0)
                        )
        );

        Update inc = update()
                .inc(col(RedPacket::getRemainCount), -1)
                .inc(col(RedPacket::getRemainAmount), record.getAmount().negate())
                .set("children.$.status", 1);

        long updated = this.updateFirst(eq, inc);
        // 如果更新条数为0则没抢到。
        if (updated != 1)
            return ApiResult.error();

        // 抢完收尾：把 remainCount 减到 0 的那一次，顺手把红包 0->1（已抢完）。
        // 条件里带 remainCount==0，天然幂等：只有真正抢空的那次能匹配到，其余次匹配 0 条无副作用。
        this.getMongoTemplate().updateFirst(
                new Query(Criteria.where("_id").is(record.getPacketId())
                        .and("status").is(0)
                        .and("remainCount").is(0)),
                new Update().set("status", 1),
                RedPacket.class);

        // 记录钱包流水
        WalletFlow walletFlow = new WalletFlow().setUserId(record.getUserId())
                .setInOut(1)
                .setBizType(2)
                .setBizDetailId(record.getId().toHexString())
                .setRemark("红包");
        this.getMongoTemplate().insert(walletFlow);


        // 抢到红包，给用户加钱
        Query query = eq(
                where(col(WalletInfo::getUserId)).is(record.getUserId())
        );
        Update update = update().inc(col(WalletInfo::getBalance), record.getAmount());
        this.getMongoTemplate().updateFirst(query,update,WalletInfo.class);


        // 更新红包记录
        this.getMongoTemplate().updateFirst(
                eq(where(col(RedPacketRecord::getId)).is(record.getId())),
                update().set("status", 1),
                RedPacketRecord.class);
        return ApiResult.success();
    }


    public List<RedPacketItem> split(LinkSendPacketDto dto, String redPacketId, long now){
        if (dto.getCount() <= 0)
            throw new IllegalStateException("红包份数必须大于1");

        if (dto.getAmount() == null || dto.getAmount().signum() <= 0)
            throw new IllegalArgumentException("红包金额必须 > 0");

        int count = dto.getCount();
        BigDecimal totalAmount = dto.getAmount();
        // 元 -> 分，用 movePointRight(2)（×100）；绝不用 BigDecimal 直接除，避免除不尽丢分
        long totalFen = totalAmount.movePointRight(2).setScale(0, BigDecimal.ROUND_DOWN).longValueExact();
        if (totalFen < count)
            throw new IllegalArgumentException("金额太小，无法拆成 " + count + " 份（每份至少 1 分）");

        List<RedPacketItem> items = splitFen(dto,totalFen,redPacketId, count,now);

        // 统一把「分」转成「元」，转换后再推 Redis，保证 Redis 与 DB(children) 金额完全一致
        for (RedPacketItem item : items) {
            item.setAmount(BigDecimal.valueOf(item.getAmount().longValue(), 2)); // 分 -> 元，如 250 -> 2.50
            redisTemplate.opsForList().rightPush(RedisKeys.RED_PACKET + redPacketId, item);
        }
        return items;
    }

    private List<RedPacketItem> splitFen(LinkSendPacketDto dto, long totalFen, String redPacketId, int count, long now) {
        long remainFen = totalFen;
        int remainCount = count;
        ArrayList<RedPacketItem> items = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            long amount;
            if (remainCount == 1){
                // 最后一份拿走剩余全部 —— 金额守恒的兜底，保证 Σ 每份 == 总额，一分不差
                amount = remainFen;
            } else {
                long max = remainFen / remainCount * 2;
                long upper = Math.min(max, remainFen - (remainCount - 1));
                if (upper < 1){
                    upper = 1;
                }
                amount = 1 + random.nextLong(upper);
            }

            // 这里 amount 仍是「分」，先按分存进 item，split() 里再统一转元
            RedPacketItem redPacketItem = new RedPacketItem().setRedPacketId(new ObjectId(redPacketId))
                    .setAmount(new BigDecimal(amount)).setStatus(0);
            redPacketItem.setId(new ObjectId(LinkID.nextIdStr()));
            items.add(redPacketItem);

            remainFen -= amount;
            remainCount--;
        }
        return items;
    }

    private static RedPacket createRedPacket(LinkSendPacketDto dto, String redPacketId, long now) {
        RedPacket redPacket = new RedPacket()
                .setSenderId(new ObjectId(dto.getSndId()))
                .setChatId(new ObjectId(dto.getChatId()))
                .setBizType(dto.getType())
                .setTotalAmount(dto.getAmount())
                .setTotalCount(dto.getCount())
                .setRemainAmount(dto.getAmount())
                .setRemainCount(dto.getCount())
                .setStatus(0)
                .setExpireTime(now + (1000 * 60 * 60 * 24))
                .setBlessing(dto.getBlessing());
        redPacket.setId(new ObjectId(redPacketId));
        return redPacket;
    }


}

