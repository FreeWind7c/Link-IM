package com.link.restapi.module.wallet.service;

import com.link.base.entity.wallet.*;
import com.link.common.pager.Pager;

import com.link.common.constants.wallet.WalletFlowBizTypeKeys;
import com.link.common.constants.wallet.WalletInOutKeys;
import com.link.common.constants.wallet.WalletTopUpStatusKeys;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.enums.wallet.WalletApiCode;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.wallet.model.dto.LinkMyselfWalletDto;
import com.link.restapi.module.wallet.model.dto.LinkWalletTopUpDto;
import com.link.restapi.module.wallet.model.dto.LinkWalletWithdrawDto;
import com.link.restapi.module.wallet.model.vo.LinkMyselfWalletInfoVo;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月07日
 */
@Slf4j
@Service
public class WalletInfoService extends BasePlatFormMongoService<WalletInfo> {



    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ApplicationContext context;


    @Transactional
    public ApiResult topUp(LinkWalletTopUpDto dto) {
        if (!stringValidator(dto.getUserId(),dto.getSecretKey()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Query eq = eq(
                where(col(WalletTopUp::getUserId)).is(new ObjectId(dto.getUserId()))
                        .and(col(WalletTopUp::getSecretKey)).is(dto.getSecretKey())
                        .and(col(WalletTopUp::getStatus)).is(WalletTopUpStatusKeys.UNREDEEMED)
        );
        Update update = update().set(col(WalletTopUp::getStatus), WalletTopUpStatusKeys.REDEEMED);
        FindAndModifyOptions options = options();
        options.upsert(false);
        options.returnNew(true);
        WalletTopUp walletTopUp = this.getMongoTemplate().findAndModify(eq, update, options, WalletTopUp.class);

        if (walletTopUp == null || walletTopUp.getStatus() != WalletTopUpStatusKeys.REDEEMED)
            return ApiResult.error(WalletApiCode.SECRET_KEY_NOT_EXIST);

        Query query = eq(
                where(col(WalletInfo::getUserId)).is(new ObjectId(dto.getUserId()))
        );
        Update inc = update.inc(col(WalletInfo::getBalance), walletTopUp.getAmount());
        FindAndModifyOptions optionsed = options();
        optionsed.upsert(false);
        optionsed.returnNew(true);
        WalletInfo walletInfo = this.findAndModify(query, inc, optionsed);

        if (walletInfo == null)
            throw new RuntimeException("用户钱包不存在");

        WalletFlow walletFlow = new WalletFlow();
        walletFlow.setUserId(new ObjectId(dto.getUserId()))
                .setAmount(walletTopUp.getAmount())
                .setInOut(WalletInOutKeys.IN)
                .setBizType(WalletFlowBizTypeKeys.TOP_UP)
                .setBizDetailId(walletTopUp.getId().toHexString())
                .setTimestamp(now())
                .setCreatedTime(now());
        this.getMongoTemplate().insert(walletFlow);

        return ApiResult.success().setData(walletInfo.getBalance());
    }

    @Transactional
    public ApiResult withdraw(LinkWalletWithdrawDto dto) {
        //  1.验证支付密码是否正确，这个地方我先掠过

        String key = dto.getUserId() + dto.getWithdrawId();

        if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, "1", 30L,TimeUnit.SECONDS))){
            long now = now();
            try{
                // 扣钱
                Query eq = eq(where(col(WalletInfo::getUserId)).is(dto.getUserId()).andOperator(
                        where(col(WalletInfo::getBalance)).gte(dto.getAmount())
                ));
                Update inc = update().inc(col(WalletInfo::getBalance), dto.getAmount() * -1);
                FindAndModifyOptions options = new FindAndModifyOptions();
                options.upsert(false);
                options.returnNew(true);
                WalletInfo walletInfo = this.findAndModify(eq, inc, options);

                if (walletInfo == null)
                    return ApiResult.error("余额不足");

                // 记录账单流水
                WalletFlow walletFlow = new WalletFlow().setUserId(new ObjectId(dto.getUserId()))
                        .setAmount(new BigDecimal(dto.getAmount()))
                        .setBizType(6) // 6代表体现
                        .setInOut(0) // 表示支出
                        .setBizDetailId(dto.getWithdrawId())
                        .setRemark("用户提现");
                this.getMongoTemplate().insert(walletFlow);

                // 新增用户提现记录
                WalletWithdraw withdraw = new WalletWithdraw().setUserId(walletInfo.getUserId())
                        .setStatus(0)
                        .setWithdrawId(dto.getWithdrawId())
                        .setAmount(new BigDecimal(dto.getAmount()))
                        .setDueTime(now + (30 * 1000))
                        .setRemake("用户提现");
                WalletWithdraw walletWithdraw = this.getMongoTemplate().insert(withdraw);

                // 模拟打款操作，通过mq异步打款，我暂时将他卸载了RemotePushPublisher类的walletWithdraw，假设这就是mq消费的打款操作
                this.rabbitTemplate.convertAndSend("wallet.withdraw.key","wallet.exchange",walletWithdraw);
                return ApiResult.success().setData(walletInfo);
            }catch (Exception e){
                // 删除redis的key，如果不删除，那么这次失败，下次操作会被幂等性给直接返回
                redisTemplate.delete(key);
                // 抛出异常，如果不跑出异常则事务注解会失效
                throw new RuntimeException("提现失败");
            }

        }


        return ApiResult.error();
    }

    // 打款操作
    public void walletWithdraw(WalletWithdraw withdraw){

        // 未处理成功才进行操作
        if (withdraw.getStatus() == 0){
            // 模拟打款操作。。。
            boolean success = true;

            // 打款成功更新status = 1
            if (success){
                // 假设打款成功，更新提现单状态
                Query eq = new Query(Criteria.where("_id").is(withdraw.getId()));
                Update update = new Update().set("status", 1);
                this.getMongoTemplate().updateFirst(eq,update,WalletWithdraw.class);
            }else
            {
                // 打款失败,退回提现金额，新增提现退款流水

                // 记录退款记录,退款记录根据userId和businessId做唯一所以，防止重复添加
                WalletRefund walletRefund = new WalletRefund().setUserId(withdraw.getUserId())
                        .setBizDetailId(withdraw.getId()) // 这里记录的是提现单id
                        .setStatus(0)
                        .setBizType(1) // 提现退款
                        .setAmount(withdraw.getAmount())
                        .setDueTime( System.currentTimeMillis() + (30 * 1000));
                WalletRefund refund = this.getMongoTemplate().insert(walletRefund);

                // 完成退款（refund() 内部用 CAS 抢退款单 0->1，抢到才加钱，天然只退一次）
                context.getBean(WalletInfoService.class).refund(withdraw,refund);

                // 更新提现单为 打款失败，已退款
                Query eq = new Query(Criteria.where("_id").is(withdraw.getId()));
                Update up = new Update().set("status", 2);
                this.getMongoTemplate().updateFirst(eq,up,WalletWithdraw.class);


            }
        }

    }

    @Transactional
    public void refund(WalletWithdraw withdraw, WalletRefund refund) {
        // ① CAS 抢占：把退款单从 0 抢到 1。数据库串行处理，只有一个线程能拿到 modifiedCount==1
        Query cas = new Query(Criteria.where("_id").is(refund.getId()).and("status").is(0));
        Update done = new Update().set("status", 1);
        UpdateResult result = this.getMongoTemplate().updateFirst(cas, done, WalletRefund.class);

        // ② 没抢到（别人/上一次已经退过了），直接退出，绝不重复加钱
        if (result.getModifiedCount() != 1) {
            return;
        }

        // ③ 走到这里全局保证只有一次，安全加钱
        Query eq = new Query(Criteria.where("user_id").is(withdraw.getUserId()));
        Update inc = new Update().inc("balance", withdraw.getAmount());
        this.getMongoTemplate().updateFirst(eq, inc, WalletInfo.class);

        // ④ 记退款到账流水
        WalletFlow walletFlow = new WalletFlow().setUserId(withdraw.getUserId())
                .setAmount(withdraw.getAmount())
                .setInOut(1)
                .setBizType(7)
                .setBizDetailId(refund.getId().toHexString())
                .setRemark("提现退款到账");
        this.getMongoTemplate().insert(walletFlow);
    }

    /** 流水分页：不传 limit 时的默认条数 */
    private static final int FLOW_LIMIT_DEFAULT = 20;

    /** 流水分页：单页条数上限，别让前端一次把库拉空 */
    private static final int FLOW_LIMIT_MAX = 100;

    public ApiResult myselfWallet(LinkMyselfWalletDto dto) {
        if (!stringValidator(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        ObjectId userId = new ObjectId(dto.getUserId());
        WalletInfo walletInfo = this.findOne(eq(where(col(WalletInfo::getUserId)).is(userId)));

        if (walletInfo == null)
            return ApiResult.success().setData(new LinkMyselfWalletInfoVo().setCreated(false));

        int limit = dto.getLimit() == null
                ? FLOW_LIMIT_DEFAULT
                : Math.min(Math.max(dto.getLimit(), 1), FLOW_LIMIT_MAX);

        Criteria criteria = where(col(WalletFlow::getUserId)).is(userId);
        if (stringValidator(dto.getCursor()))
            criteria.and(col(WalletFlow::getId)).lt(new ObjectId(dto.getCursor()));

        Query flowQuery = eq(criteria);
        flowQuery.with(Sort.by(Sort.Direction.DESC, col(WalletFlow::getId)));
        flowQuery.limit(limit + 1);

        List<WalletFlow> flows = this.getMongoTemplate().find(flowQuery, WalletFlow.class);

        boolean hasMore = flows.size() > limit;
        if (hasMore) flows = flows.subList(0, limit);

        String nextCursor = hasMore ? flows.get(flows.size() - 1).getId().toHexString() : null;


        Pager<WalletFlow> pager = new Pager<WalletFlow>()
                .setNextCursor(nextCursor)
                .setHasMore(hasMore)
                .setList(flows);
        LinkMyselfWalletInfoVo vo = new LinkMyselfWalletInfoVo().setBalance(walletInfo.getBalance()).setPager(pager).setCreated(true);
        return ApiResult.success().setData(vo);
    }
}
