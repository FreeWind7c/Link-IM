package com.link.restapi.module.redpack.service;

import com.google.gson.Gson;
import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.data.RedPacketData;
import com.link.base.entity.data.system.SystemGrabRedPacketData;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.message.type.MessageType;
import com.link.base.entity.redpack.RedPacket;
import com.link.base.entity.redpack.RedPacketItem;
import com.link.base.entity.redpack.RedPacketRecord;
import com.link.base.entity.wallet.WalletFlow;
import com.link.base.entity.wallet.WalletInfo;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.constants.redpack.RedPacketStatusKeys;
import com.link.common.constants.wallet.WalletFlowBizTypeKeys;
import com.link.common.constants.wallet.WalletInOutKeys;
import com.link.common.redis.RedisKeys;
import com.link.restapi.enums.redpack.RedPacketApiCode;
import com.link.restapi.module.redpack.exception.RedPackBizException;
import com.link.restapi.module.redpack.model.dto.LinkGrabPacketDto;
import com.link.restapi.module.redpack.model.dto.LinkSendPacketDto;

import com.link.restapi.utils.ApiResult;
import com.mongodb.client.result.UpdateResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@Slf4j
@Component
public class RedPacketTxExecutorService extends BasePlatFormMongoService<RedPacket> {


    /** 写冲突重试次数 */
    private static final int TX_MAX_RETRY = 3;

    private static final int PACKET_TYPE_LUCKY = 2;

    private static final String SYSTEM_MESSAGE_SUMMARY = "[红包]";


    private static final long PACKET_EXPIRE_MILLIS = 10 * 1000; //  24L * 60 * 60 * 1000

    @Autowired
    private BasePlatFormRedisService redisService;


    @Transactional(rollbackFor = Exception.class)
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = TX_MAX_RETRY,
            backoff = @Backoff(delay = 50, multiplier = 1.5)
    )
    public RedPacketTxExecutorService.GrabOutcome settleGrab(RedPacket packet, RedPacketItem item, LinkGrabPacketDto dto,
                                  String sysMessageId, long sysSeq) {
        long now = now();
        ObjectId packetId = packet.getId();
        ObjectId userId = new ObjectId(dto.getUserId());
        String inventoryKey = RedisKeys.RED_PACKET + packetId.toHexString();

        // ① 插入抢红包记录（每个用户一条记录，不冲突）
        RedPacketRecord record = new RedPacketRecord()
                .setPacketId(packetId)
                .setUserId(userId)
                .setBest(item.isBest())
                .setAmount(item.getAmount());
        record.setCreatedTime(now);
        this.getMongoTemplate().insert(record);

        UpdateResult credit = this.getMongoTemplate().updateFirst(
                eq(where(col(WalletInfo::getUserId)).is(userId)),
                update().inc(col(WalletInfo::getBalance), item.getAmount()),
                WalletInfo.class);

        if (credit.getMatchedCount() != 1)
            throw new RedPackBizException(ApiResult.error(RedPacketApiCode.WALLET_NOT_EXIST));

        // ③ 收入流水（每个用户一条，不冲突）
        this.getMongoTemplate().insert(buildFlow(userId, item.getAmount(), WalletInOutKeys.IN,
                record.getId().toHexString(), "抢红包", now));

        // ④ 检查是否抢完（从 Redis 读取实时库存）
        Long remainInRedis = redisService.getListSize(inventoryKey);
        boolean soldOut = (remainInRedis != null && remainInRedis == 0);

        int remainCount = remainInRedis != null ? remainInRedis.intValue() : 0;
        BigDecimal remainAmount = BigDecimal.ZERO;

        String targetMessageId = packet.getMessageId() != null ? packet.getMessageId().toHexString() : dto.getMessageId();
        BaseMessage sysMessage = null;
        if (sysMessageId != null) {
            sysMessage = buildGrabSystemMessage(packet, dto, targetMessageId, sysMessageId, sysSeq, now);
            this.getMongoTemplate().insert(sysMessage);
            touchChatSession(packet.getChatId(), sysMessage, SYSTEM_MESSAGE_SUMMARY, now);
        }

        return new GrabOutcome(remainCount, remainAmount,
                soldOut ? RedPacketStatusKeys.SOLD_OUT : RedPacketStatusKeys.IN_PROGRESS,
                targetMessageId, sysMessage,soldOut);
    }

    @Transactional(rollbackFor = Exception.class)
    @Retryable(
            retryFor = {RuntimeException.class},
            maxAttempts = TX_MAX_RETRY,
            backoff = @Backoff(delay = 50, multiplier = 1.5)
    )
    public RedPacketTxExecutorService.SendOutcome doSend(LinkSendPacketDto dto, String packetId, String rcvId,
                              String messageId, long seq, long now) {

        // ① 扣款。密码、禁用、余额三个条件全压进 query 里做单条 CAS：
        //    先查后判再更新会在「判完」和「更新」之间留出窗口，两笔并发能把余额扣穿
        Query cas = eq(where(col(WalletInfo::getUserId)).is(new ObjectId(dto.getSndId()))
                .and(col(WalletInfo::getPassword)).is(dto.getPassword())
                .and(col(WalletInfo::isDisabled)).is(false)
                .and(col(WalletInfo::getBalance)).gte(dto.getAmount()));
        UpdateResult debit = this.getMongoTemplate().updateFirst(cas,
                update().inc(col(WalletInfo::getBalance), dto.getAmount().negate()), WalletInfo.class);

        // 没扣成只知道「四个条件里有一个不满足」，直接返回通用错误
        // 注意：在事务中无法使用非 primary 读偏好进行诊断查询，所以简化错误提示
        if (debit.getModifiedCount() != 1)
            throw new RedPackBizException(ApiResult.error(RedPacketApiCode.PAYMENT_PASSWORD_ERROR));

        // ② 建红包。bizDetailId 上的唯一索引是重复扣款的最终防线
        RedPacket packet = createRedPacket(dto, packetId, rcvId, messageId, now);
        this.insert(packet);

        // ③ 支出流水
        this.getMongoTemplate().insert(buildFlow(new ObjectId(dto.getSndId()), dto.getAmount(),
                WalletInOutKeys.OUT, packetId, "发红包", now));

        // ④ 会话里的红包气泡
        BaseMessage message = buildRedPacketMessage(dto, packetId, rcvId, messageId, seq, now);
        this.getMongoTemplate().insert(message);
        touchChatSession(dto.getChatId(), message, MessageType.summaryOf(message.getType(), message.getBaseData()), now);

        return new SendOutcome(packet, message);
    }


    /**
     * 更新会话的「最后一条消息」。
     *
     * <p>summary 显式传进来而不是内部算：系统消息（{@code MessageType.SYSTEM_MESSAGE}）的
     * summaryLabel 是 null，直接用 {@code summaryOf} 会把会话列表的预览刷成空白。
     */
    private void touchChatSession(String chatId, BaseMessage message, String summary, long now) {
        this.getMongoTemplate().updateFirst(
                eq(where(col(ChatSession::getChatId)).is(chatId)),
                update().set(col(ChatSession::getLastMsgSeq), message.getSeq())
                        .set(col(ChatSession::getLastMsgType), message.getType())
                        .set(col(ChatSession::getLastMsgSummary), summary)
                        .set(col(ChatSession::getLastMsgTime), now),
                ChatSession.class);
    }

    private WalletFlow buildFlow(ObjectId userId, BigDecimal amount, int inOut,
                                 String bizDetailId, String remark, long now) {
        WalletFlow flow = new WalletFlow()
                .setUserId(userId)
                .setAmount(amount)
                .setInOut(inOut)
                .setBizType(WalletFlowBizTypeKeys.RED_PACK)
                .setBizDetailId(bizDetailId)
                .setRemark(remark)
                .setTimestamp(now);
        flow.setCreatedTime(now);
        return flow;
    }

    private BaseMessage buildGrabSystemMessage(RedPacket packet, LinkGrabPacketDto dto, String packetMessageId,
                                               String messageId, long seq, long now) {
        SystemGrabRedPacketData data = new SystemGrabRedPacketData()
                .setSndId(packet.getSndId().toHexString())
                .setClaimantId(dto.getUserId())
                .setPacketMessageId(packetMessageId);
        return new DefaultMessageInfo()
                .setId(new ObjectId(messageId))
                .setSeq((int) seq)
                .setType(MessageType.SYSTEM_MESSAGE.getType())
                .setChatId(packet.getChatId())
                .setSndId(packet.getSndId())
                .setRcvId(packet.getRcvId())
                .setState(1)
                .setBaseData(data)
                .setData(new Gson().toJson(data))
                .setQuote(null)
                .setTimestamp(now);
    }

    private RedPacket createRedPacket(LinkSendPacketDto dto, String packetId, String rcvId,
                                      String messageId, long now) {
        RedPacket packet = new RedPacket()
                .setBizDetailId(dto.getBizDetailId())
                .setMessageId(new ObjectId(messageId))
                .setSndId(new ObjectId(dto.getSndId()))
                .setRcvId(new ObjectId(dto.getRcvId()))
                .setChatId(dto.getChatId())
                .setBizType(dto.getType())
                .setTotalAmount(dto.getAmount())
                .setTotalCount(dto.getCount())
                .setRemainAmount(dto.getAmount())
                .setRemainCount(dto.getCount())
                .setStatus(RedPacketStatusKeys.IN_PROGRESS)
                .setExpireTime(now + PACKET_EXPIRE_MILLIS)
                .setBlessing(dto.getBlessing());
        packet.setId(new ObjectId(packetId));
        packet.setCreatedTime(now);
        return packet;
    }

    private BaseMessage buildRedPacketMessage(LinkSendPacketDto dto, String packetId, String rcvId,
                                              String messageId, long seq, long now) {
        // 消息的 data 只存创建时的静态快照，不含 status 和 claimantIds
        RedPacketData data = new RedPacketData()
                .setId(packetId)
                .setTotalAmount(dto.getAmount())
                .setBlessing(dto.getBlessing())
                .setTotalCount(dto.getCount());

        BaseMessage message = dto.getType() == PACKET_TYPE_LUCKY
                ? new GroupMessageInfo() : new DefaultMessageInfo();
        // insert 按运行时类型选集合：GroupMessageInfo -> group_message_queue，DefaultMessageInfo -> default_message_queue
        return message.setId(new ObjectId(messageId))
                .setSeq((int) seq)
                .setType(MessageType.RED_PACK_MESSAGE.getType())
                .setChatId(dto.getChatId())
                .setSndId(new ObjectId(dto.getSndId()))
                .setRcvId(new ObjectId(dto.getRcvId()))

                .setState(1)
                // 红包不可引用（MessageType.RED_PACK_MESSAGE.quotable = false）
                .setQuote(null)
                .setData(new Gson().toJson(data))
                .setBaseData(data)
                .setTimestamp(now);

    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GrabOutcome {
        private int remainCount;
        private BigDecimal remainAmount;
        private int packetStatus;
        private String packetMessageId;
        private BaseMessage sysMessage;
        public boolean soldOut;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendOutcome {
        private RedPacket packet;
        private BaseMessage message;
    }


}
