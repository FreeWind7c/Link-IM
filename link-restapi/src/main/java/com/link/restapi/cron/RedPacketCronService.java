package com.link.restapi.cron;

import com.link.common.redis.RedisKeys;
import com.link.common.util.id.SnowflakeIdGenerator;
import com.link.im.entity.redpack.RedPacket;
import com.link.im.entity.redpack.RedPacketRecord;
import com.link.im.entity.wallet.WalletFlow;
import com.link.im.entity.wallet.WalletInfo;
import com.link.restapi.redpack.service.RedPackService;
import com.mongodb.client.result.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */

@Component
public class RedPacketCronService {


    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedPackService redPackService;


    /**
     * 扫描红包记录，查看是否有已抢到红包，但是未给用户打钱的记录
     */
    @Scheduled(fixedRate =  60 * 1000)
    public void verifyRedPacketRecord(){
        long now = System.currentTimeMillis();
        // 字段无 @Field，库里是驼峰 dutTime，不能写 dut_time（写了永远查不到）
        List<RedPacketRecord> records = this.mongoTemplate.find(new Query(Criteria.where("status").is(0)
                .and("dutTime").lte(now)), RedPacketRecord.class);
        for (RedPacketRecord record : records) {
            if (record.getStatus() == 0) {
                redPackService.byTrx(record);
            }
        }
    }


    /**
     * 扫描过期红包
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void verifyRedPacket(){
        long now = System.currentTimeMillis();
        // 字段无 @Field，用驼峰 expireTime/remainCount；已过期是 expireTime <= now（不是 >=）
        List<RedPacket> redPackets = this.mongoTemplate.find(new Query(Criteria.where("expireTime").lte(now)
                .and("status").is(0).and("remainCount").gt(0)), RedPacket.class);
        for (RedPacket v : redPackets) {

            // CAS：把红包 0->2 并翻子红包，同时用 findAndModify 原子拿到「翻转前」的文档。
            // 只有抢到翻转权(旧文档非 null)的线程才退款；退款金额取翻转瞬间的真实 remainAmount，
            // 而不是 find 时的旧快照，避免与并发抢红包竞态导致超额退款。
            Query eq = new Query(Criteria.where("_id").is(v.getId()).and("status").is(0));
            Update update = new Update()
                    .set("status", 2)
                    .set("children.$[item].status", 2)
                    .filterArray(Criteria.where("item.status").is(0));

            FindAndModifyOptions opt = new FindAndModifyOptions().returnNew(false); // 要翻转前的文档
            RedPacket old = this.mongoTemplate.findAndModify(eq, update, opt, RedPacket.class);
            if (old == null)
                continue; // 没抢到翻转权（别人/上一轮已处理），跳过，绝不重复退款

            BigDecimal remainAmount = old.getRemainAmount(); // 翻转瞬间的真实剩余
            // 红包剩余金额原路退回发送者
            Query query = new Query(Criteria.where("user_id").is(v.getSenderId()));
            Update inc = new Update().inc("balance", remainAmount);
            this.mongoTemplate.updateFirst(query,inc, WalletInfo.class);

            // 删除redis中的红包信息
            redisTemplate.delete(RedisKeys.RED_PACKET+v.getId().toHexString());

        }
    }



}
