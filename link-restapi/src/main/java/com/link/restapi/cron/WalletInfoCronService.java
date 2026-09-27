package com.link.restapi.cron;

import com.link.base.entity.wallet.WalletWithdraw;
import com.link.restapi.module.wallet.service.WalletInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Component
public class WalletInfoCronService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private WalletInfoService walletInfoService;

    @Autowired
    private ApplicationContext context;




    // 定时扫描提现单,为了去报提现操作被成功执行
    @Scheduled(fixedRate = 35 * 1000)
    public void verifyWalletWithdraw(){
        long now = System.currentTimeMillis();
        // 查询是否有超时未打款的提现单
        List<WalletWithdraw> walletWithdraws = this.mongoTemplate.find(new Query(Criteria.where("status").is(0).and("due_time").lte(now)), WalletWithdraw.class);
        for (WalletWithdraw item : walletWithdraws) {
            String key = item.getId().toHexString();
            // 校验是否已经处理过
            if ( Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", 30L,TimeUnit.SECONDS))  && item.getStatus() == 0 ){
                // 执行打款操作
                walletInfoService.walletWithdraw(item);
                // 处理成功加入redis
                redisTemplate.opsForValue().setIfAbsent(key,"1", 60,TimeUnit.MINUTES);
            }
        }
    }

}
