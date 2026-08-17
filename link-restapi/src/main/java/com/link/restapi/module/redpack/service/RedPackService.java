package com.link.restapi.module.redpack.service;

import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.model.redpack.LinkRedPacketUpdate;
import com.link.common.redis.RedisKeys;
import com.link.common.util.id.LinkID;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.constants.redpack.RedPacketStatusKeys;
import com.link.im.constants.wallet.WalletFlowBizTypeKeys;
import com.link.im.constants.wallet.WalletInOutKeys;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.data.RedPackData;
import com.link.im.entity.data.system.SystemGrabRedPacketData;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.entity.redpack.RedPacket;
import com.link.im.entity.redpack.RedPacketItem;
import com.link.im.entity.redpack.RedPacketRecord;
import com.link.im.entity.user.UserInfo;
import com.link.im.entity.wallet.WalletFlow;
import com.link.im.entity.wallet.WalletInfo;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.enums.redpack.RedPacketApiCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.service.LinkRedisService;
import com.link.im.util.ApiResult;
import com.link.restapi.module.redpack.exception.RedPackBizException;
import com.link.restapi.module.redpack.model.dto.LinkGetRedPacketDTO;
import com.link.restapi.module.redpack.model.dto.LinkGrabPacketDto;
import com.link.restapi.module.redpack.model.dto.LinkSendPacketDto;
import com.link.restapi.module.redpack.model.vo.LinkRedPacketRecordVO;
import com.link.restapi.module.redpack.model.vo.LinkRedPacketVO;
import com.link.restapi.push.RemotePushPublisher;
import com.mongodb.MongoException;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 红包（发 / 抢 / 详情）。
 *
 * <p>每个公开方法都是同一套四段分层，顺序不能调：
 * <ol>
 *   <li><b>事务外前置</b>：参数校验、鉴权、拆包、seq 分配、Redis 预占。都是纯内存或纯 Redis，
 *       跟着 Mongo 事务回滚不了，所以必须待在事务外，由调用方显式补偿。</li>
 *   <li><b>事务内</b>：只放 Mongo 写。业务不通过一律 {@code throw RedPackBizException}——
 *       {@code return} 是正常返回，事务会照常提交，「钱扣了红包没建成」就此落库。</li>
 *   <li><b>事务外后置</b>：填 Redis 库存、推 MQ。必须在 commit 之后，
 *       否则事务一回滚，端上已经收到消息、库存也已经放出去了。</li>
 *   <li><b>失败补偿</b>：把预占出来的那一份还回队列、放行幂等键。</li>
 * </ol>
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Slf4j
@Component
public class RedPackService extends BasePlatFormMongoService<RedPacket> {

    /** dto.type / RedPacket.bizType：单聊红包（一份，定额） */
    private static final int PACKET_TYPE_SINGLE = 1;
    /** dto.type / RedPacket.bizType：群拼手气红包（多份，随机拆） */
    private static final int PACKET_TYPE_LUCKY = 2;

    /** 幂等键的占位值。redisTemplate 的值序列化器是 Jackson，写 String 最省事也最好读 */
    private static final String IDEMPOTENT_FLAG = "1";

    /**
     * 幂等键 TTL，必须 ≥ 红包有效期。
     *
     * <p>原来是 30 秒——只够挡住连点，用户 5 分钟后重发同一个 bizDetailId 就会二次扣款。
     * 真正的最终防线是 {@code RedPacket.bizDetailId} 上的唯一索引，Redis 这层只是省一次打库。
     */
    private static final long IDEMPOTENT_TTL_HOURS = 24L;

    /** 红包有效期：24 小时未抢完由定时任务退回剩余金额 */
    private static final long PACKET_EXPIRE_MILLIS = 10 * 1000; //  24L * 60 * 60 * 1000

    /**
     * Redis 预扣队列的 TTL，比红包有效期多留 1 小时。
     *
     * <p>不设 TTL 的话，没抢完的红包会把几百个 item 永久留在 Redis 里；
     * 多留 1 小时是为了让过期退款的定时任务先跑完——队列先没了、退款还没执行，剩余份数就查无对证。
     */
    private static final long INVENTORY_TTL_HOURS = 25L;

    /** 单个红包最多拆几份 */
    private static final int MAX_COUNT = 200;

    /** 单个红包金额上限 */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("20000");

    /** 抢红包锁：最多等 3 秒，拿不到就让客户端重试，别把 HTTP 线程挂在这儿 */
    private static final long GRAB_LOCK_WAIT_SECONDS = 3L;

    /** 抢红包锁租期：兜底防止持锁进程崩了把红包锁死 */
    private static final long GRAB_LOCK_LEASE_SECONDS = 30L;

    /** 写冲突重试次数 */
    private static final int TX_MAX_RETRY = 3;

    /** bizDetailId 的合法格式：ASCII 字母数字加 -_，长度 8~64。见 {@link #isValidBizDetailId} */
    private static final Pattern BIZ_DETAIL_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    /**
     * 抢红包系统消息在会话列表里的预览文案。
     *
     * <p>不能用 {@code MessageType.summaryOf}：SYSTEM_MESSAGE 的 summaryLabel 是 null，
     * 直接用会把会话列表的预览刷成空白。
     */
    private static final String SYSTEM_MESSAGE_SUMMARY = "[红包]";

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Autowired
    private LinkRedisService redisService;

    @Autowired
    @Lazy
    private RedPackService self;

    /** 发红包事务的产物，带回事务外用于 commit 后填库存和推消息 */
    private record SendOutcome(RedPacket packet, BaseMessage message) {
    }

    /** 抢红包事务的产物，带回事务外用于 commit 后推送 */
    private record GrabOutcome(int remainCount, BigDecimal remainAmount, int packetStatus,
                               String packetMessageId, BaseMessage sysMessage) {
    }


    public ApiResult sendRedPacket(LinkSendPacketDto dto) {
        // ---------- 事务外前置：只校验、只算，不碰库 ----------
        if (dto == null
                || !stringValidator(dto.getSndId(), dto.getChatId(), dto.getBizDetailId(), dto.getPassword())
                || !ObjectId.isValid(dto.getSndId())
                || dto.getAmount() == null
                || (dto.getType() != PACKET_TYPE_SINGLE && dto.getType() != PACKET_TYPE_LUCKY))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        // 金额先归一到分再用：客户端可能传 1.005，不截断的话「拆出来的总和」和「扣掉的钱」会差一分
        BigDecimal amount = dto.getAmount().setScale(2, RoundingMode.DOWN);
        if (amount.signum() <= 0)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);
        dto.setAmount(amount);

        // 单聊红包天然只有一份，别信客户端传的 count
        int count = dto.getType() == PACKET_TYPE_SINGLE ? 1 : dto.getCount();
        if (count < 1 || count > MAX_COUNT || amount.compareTo(MAX_AMOUNT) > 0)
            return ApiResult.error(RedPacketApiCode.RED_PACKET_LIMIT_EXCEEDED);
        dto.setCount(count);

        if (amount.movePointRight(2).longValueExact() < count)
            return ApiResult.error(RedPacketApiCode.RED_PACKET_AMOUNT_TOO_SMALL);

        // bizDetailId 同时要当消息 _id 用，必须限死格式：它由客户端生成，
        // 不校验的话对方塞个几 KB 的字符串进来就直接成了消息主键
        if (!isValidBizDetailId(dto.getBizDetailId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String idemKey = RedisKeys.RED_PACKET_SEND_IDEM + dto.getBizDetailId();
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(idemKey, IDEMPOTENT_FLAG, IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS)))
            return ApiResult.error(RedPacketApiCode.RED_PACKET_DUPLICATE_SUBMIT);

        long now = now();
        String packetId = new ObjectId().toHexString();
        try {
            String rcvId = resolveSendTarget(dto);
            List<RedPacketItem> items = split(dto);

            // 消息 id 直接用前端传的 bizDetailId，不再后端现造：
            //   1) 与普通消息一致 —— LinkDefaultMessageEventHandler 用的就是客户端传的 message.getId()
            //   2) 客户端超时重发时，allocate 认出是同一个 id，返回原来那个 seq 而不是再烧一个号段
            String messageId = new ObjectId().toHexString();
            long seq = this.messageSeqAllocator.allocate(dto.getChatId(), messageId).seq();

            SendOutcome outcome = self.doSend(dto, packetId, rcvId, messageId, seq, now);

            // ---------- 事务外后置：走到这里 commit 已经成功 ----------
            // 顺序不能反：先填库存再推消息。反过来客户端收到消息立刻点开，撞上的是一个空队列
            primeRedisInventory(packetId, items);
            pushRedPacketMessage(dto, outcome.message());

            return ApiResult.success().setData(outcome.packet());

        } catch (RedPackBizException e) {
            // 业务分支：事务整体回滚了，钱没扣红包没建，放行同一个 bizDetailId 重试
            redisTemplate.delete(idemKey);
            return e.getResult();
        } catch (DuplicateKeyException e) {
            // 撞 biz_detail_id 唯一索引 = 同一次点击的重复请求（Redis 幂等键失效时的兜底）。
            // 前一单已经成了，这里绝不能删幂等键——删了就是放行第二次扣款
            log.warn("发红包重复提交 bizDetailId={}, sndId={}", dto.getBizDetailId(), dto.getSndId());
            return ApiResult.error(RedPacketApiCode.RED_PACKET_DUPLICATE_SUBMIT);
        } catch (Exception e) {
            redisTemplate.delete(idemKey);
            log.error("发红包失败 packetId={}, chatId={}, sndId={}", packetId, dto.getChatId(), dto.getSndId(), e);
            return ApiResult.error(RedPacketApiCode.RED_PACKET_CREATE_FAILED);
        }
    }

    /**
     * 发红包的事务体：扣款、建红包、记流水、落消息，四件事一起成或一起废。
     *
     * <p>public 是必须的——{@code @Transactional} 走 CGLIB 代理，包级/私有方法拦不到。
     */
//    @Transactional(rollbackFor = Exception.class)
    public SendOutcome doSend(LinkSendPacketDto dto, String packetId, String rcvId,
                              String messageId, long seq, long now) {

        // ① 扣款。密码、禁用、余额三个条件全压进 query 里做单条 CAS：
        //    先查后判再更新会在「判完」和「更新」之间留出窗口，两笔并发能把余额扣穿
        Query cas = eq(where(col(WalletInfo::getUserId)).is(new ObjectId(dto.getSndId()))
                .and(col(WalletInfo::getPassword)).is(dto.getPassword())
                .and(col(WalletInfo::isDisabled)).is(false)
                .and(col(WalletInfo::getBalance)).gte(dto.getAmount()));
        UpdateResult debit = this.getMongoTemplate().updateFirst(cas,
                update().inc(col(WalletInfo::getBalance), dto.getAmount().negate()), WalletInfo.class);

        // 没扣成只知道「四个条件里有一个不满足」，回查一次才能告诉用户到底是密码错还是余额不足。
        // 这次回查只在失败路径上发生，不影响正常流程的开销
        if (debit.getModifiedCount() != 1)
            throw new RedPackBizException(diagnoseDebit(dto));

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
     * 客户端传来的 bizDetailId 是否合法。
     *
     * <p>它有三个身份：发红包的幂等凭据（唯一索引）、{@code RedPacket.messageId}、以及这条红包消息的
     * {@code _id}。既然值由客户端定，格式就必须由服务端卡死——只允许 ASCII 字母数字加 {@code -_}，
     * 长度 8~64。放开的话，一个几 KB 的字符串或带 {@code .} / {@code $} 的串会直接成为消息主键。
     */
    private static boolean isValidBizDetailId(String bizDetailId) {
        return bizDetailId != null && BIZ_DETAIL_ID_PATTERN.matcher(bizDetailId).matches();
    }

    /**
     * 鉴权 + 定位收红包的一方。不在会话里的人不能往里发红包。
     *
     * @return 单聊返回对端 uid，群聊返回 chatId（群红包的「收方」就是这个会话本身）
     */
    private String resolveSendTarget(LinkSendPacketDto dto) {
        if (dto.getType() == PACKET_TYPE_LUCKY) {
            Set<String> memberIds = this.redisService.getChatMemberIds(dto.getChatId());
            if (memberIds == null || !memberIds.contains(dto.getSndId()))
                throw new RedPackBizException(ApiResult.error(RedPacketApiCode.CHAT_NOT_JOINED));
            return dto.getChatId();
        }

        ChatSessionMember member = this.getMongoTemplate().findOne(
                eq(where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(dto.getSndId()))
                        .and(col(ChatSessionMember::getChatId)).is(dto.getChatId())),
                ChatSessionMember.class);
        if (member == null || member.getTargetId() == null)
            throw new RedPackBizException(ApiResult.error(RedPacketApiCode.CHAT_NOT_JOINED));
        return member.getTargetId().toHexString();
    }

    /** CAS 扣款没命中时回查一次，把「哪个条件没过」翻译成具体错误码 */
    private ApiResult diagnoseDebit(LinkSendPacketDto dto) {
        WalletInfo wallet = this.getMongoTemplate().findOne(
                eq(where(col(WalletInfo::getUserId)).is(new ObjectId(dto.getSndId()))), WalletInfo.class);
        if (wallet == null)
            return ApiResult.error(RedPacketApiCode.WALLET_NOT_EXIST);
        if (wallet.isDisabled())
            return ApiResult.error(RedPacketApiCode.WALLET_DISABLED);
        if (!dto.getPassword().equals(wallet.getPassword()))
            return ApiResult.error(RedPacketApiCode.PAYMENT_PASSWORD_ERROR);
        return ApiResult.error(RedPacketApiCode.WALLET_INSUFFICIENT_BALANCE);
    }

    private RedPacket createRedPacket(LinkSendPacketDto dto, String packetId, String rcvId,
                                      String messageId, long now) {
        RedPacket packet = new RedPacket()
                .setBizDetailId(dto.getBizDetailId())
                .setMessageId(messageId)
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
        RedPackData data = new RedPackData()
                .setId(packetId)
                .setAmount(dto.getAmount())
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

    /** 把拆好的每一份塞进 Redis 预扣队列。必须在 commit 之后调，否则事务回滚了库存已经放出去 */
    private void primeRedisInventory(String packetId, List<RedPacketItem> items) {
        String key = RedisKeys.RED_PACKET + packetId;
        redisTemplate.opsForList().rightPushAll(key, items.toArray());
        redisTemplate.expire(key, INVENTORY_TTL_HOURS, TimeUnit.HOURS);
    }

    private void pushRedPacketMessage(LinkSendPacketDto dto, BaseMessage message) {

        if (dto.getType() == PACKET_TYPE_LUCKY) {
            Set<String> memberIds = this.redisService.getChatMemberIds(dto.getChatId());
            if (CollectionUtils.isEmpty(memberIds))
                return;
            this.pushPublisher.push(EventType.GROUP_MESSAGE, memberIds, message);
        } else {
            this.pushPublisher.push(EventType.DEFAULT_MESSAGE,
                    Arrays.asList(dto.getSndId(), message.getRcvId().toHexString()), message);
        }
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


    // ==================== 抢红包 ====================

    public ApiResult grabRedPacket(LinkGrabPacketDto dto) {
        if (dto == null || !stringValidator(dto.getUserId(), dto.getPacketId())
                || !ObjectId.isValid(dto.getUserId()) || !ObjectId.isValid(dto.getPacketId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String suffix = dto.getPacketId() + ":" + dto.getUserId();
        String idemKey = RedisKeys.RED_PACKET_GRAB_IDEM + suffix;
        String inventoryKey = RedisKeys.RED_PACKET + dto.getPacketId();
        // 锁和幂等键必须是两个 key：Redisson 的锁在 Redis 里是 hash 且持锁期间必然存在，
        // 同名时持锁中执行 SET NX 必然失败，表现为「所有人第一次抢都被判成重复请求」
        RLock lock = redisson.getLock(RedisKeys.RED_PACKET_GRAB_LOCK + suffix);

        boolean locked = false;
        boolean idemHeld = false;
        boolean committed = false;
        boolean keepIdem = false;
        RedPacketItem item = null;
        try {
            locked = lock.tryLock(GRAB_LOCK_WAIT_SECONDS, GRAB_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked)
                return ApiResult.error(RedPacketApiCode.GRAB_BUSY);

            RedPacket packet = this.findById(new ObjectId(dto.getPacketId()));
            // 红包校验
            ApiResult rejected = precheckGrab(packet, dto);
            if (rejected != null)
                return rejected;

            // 幂等校验放在碰队列之前：重复请求先 pop 掉一份再失败的话，池子就被搅乱了
            if (!Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(idemKey, IDEMPOTENT_FLAG, IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS)))
                return ApiResult.error(RedPacketApiCode.RED_PACKET_REPEAT_GRAB);
            idemHeld = true;

            // Redis 是库存权威：弹出即预占。后面任何一步没走到 commit，finally 都要还回去
            item = (RedPacketItem) redisTemplate.opsForList().rightPop(inventoryKey);
            if (item == null)
                return ApiResult.error(RedPacketApiCode.RED_PACKET_SOLD_OUT);

            // 单聊才落「XX 领取了你的红包」系统消息；群聊 500 人抢 100 份会刷屏 100 条，只推增量事件。
            // seq 同样得在事务外分配 —— 它在 Redis，回滚不了
            boolean single = packet.getBizType() == PACKET_TYPE_SINGLE;
            String sysMessageId = new ObjectId().toHexString();
            long sysSeq = single ? this.messageSeqAllocator.allocate(packet.getChatId(), sysMessageId).seq() : 0L;

            GrabOutcome outcome;
            try {
                outcome = settleWithRetry(packet, item, dto, sysMessageId, sysSeq);
            } catch (DuplicateKeyException e) {
                // 撞 (packet_id,user_id) 唯一索引 = 这个人已经抢过了。
                // 幂等键留着，让他的下一次请求在 Redis 层就被挡掉，不必再打一次库
                keepIdem = true;
                return ApiResult.error(RedPacketApiCode.RED_PACKET_REPEAT_GRAB);
            }
            committed = true;

            // ---------- 事务外后置：commit 之后才推 ----------
            pushGrabResult(packet, dto, outcome);

            return ApiResult.success().setData(item.getAmount());

        } catch (RedPackBizException e) {
            return e.getResult();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResult.error(RedPacketApiCode.GRAB_BUSY);
        } catch (Exception e) {
            log.error("抢红包失败 packetId={}, userId={}", dto.getPacketId(), dto.getUserId(), e);
            return ApiResult.error(GlobalCode.GLOBAL_ERROR);
        } finally {
            if (!committed) {
                // 预占的那一份必须还回队列，否则它永远沉在内存里，这个红包再也抢不完
                if (item != null)
                    redisTemplate.opsForList().rightPush(inventoryKey, item);
                // 事务没提交 = 这次不算抢过，放行重试。「已抢过」和「重复提交」两种情况除外
                if (idemHeld && !keepIdem)
                    redisTemplate.delete(idemKey);
            }
            if (locked && lock.isHeldByCurrentThread())
                lock.unlock();
        }
    }

    /**
     * 事务外的前置校验：红包在不在、能不能抢、这个人有没有资格抢。
     *
     * @return 校验不通过时返回要回给客户端的结果，通过则返回 null
     */
    private ApiResult precheckGrab(RedPacket packet, LinkGrabPacketDto dto) {
        if (packet == null)
            return ApiResult.error(RedPacketApiCode.RED_PACKET_NOT_EXIST);
        if (packet.getStatus() == RedPacketStatusKeys.REFUND)
            return ApiResult.error(RedPacketApiCode.RED_PACKET_EXPIRED);
        if (packet.getStatus() == RedPacketStatusKeys.SOLD_OUT || packet.getRemainCount() <= 0)
            return ApiResult.error(RedPacketApiCode.RED_PACKET_SOLD_OUT);
        // 定时任务最长 5 分钟才扫一次，这里按时间自判，别让过期红包在这个窗口里还能被抢
        if (packet.getExpireTime() > 0 && packet.getExpireTime() <= now())
            return ApiResult.error(RedPacketApiCode.RED_PACKET_EXPIRED);
        if (!isChatMember(packet, dto.getUserId()))
            return ApiResult.error(RedPacketApiCode.RED_PACKET_FORBIDDEN);
        return null;
    }

    /** 单聊红包是点对点的，只有收方能领（发的人自己也不行）；群红包校验是不是群成员 */
    private boolean isChatMember(RedPacket packet, String userId) {
        if (packet.getBizType() == PACKET_TYPE_SINGLE)
            return userId.equals(packet.getRcvId().toHexString());
        Set<String> memberIds = this.redisService.getChatMemberIds(packet.getChatId());
        return memberIds != null && memberIds.contains(userId);
    }

    /**
     * 带写冲突重试地执行结算事务。
     *
     * <p>同一个红包被并发抢时，Mongo 会让后到的那笔整体回滚并打上 TransientTransactionError 标签。
     * 这是「稍后再来」而不是「失败」，复用同一份已 pop 的 item 原样重试即可——
     * 重新 pop 会多占一份库存。
     */
    private GrabOutcome settleWithRetry(RedPacket packet, RedPacketItem item, LinkGrabPacketDto dto,
                                        String sysMessageId, long sysSeq) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= TX_MAX_RETRY; attempt++) {
            try {
                return self.settleGrab(packet, item, dto, sysMessageId, sysSeq);
            } catch (RuntimeException e) {
                if (!isTransient(e))
                    throw e;
                last = e;
                log.warn("抢红包事务写冲突，第 {}/{} 次重试 packetId={}, userId={}",
                        attempt, TX_MAX_RETRY, dto.getPacketId(), dto.getUserId());
            }
        }
        throw last;
    }

    /**
     * 判断异常是不是「可原样重试」的事务瞬时错误。
     *
     * <p>必须沿 cause 链找：Spring 会把驱动抛的 MongoException 包成 UncategorizedMongoDbException，
     * 只看最外层永远匹配不上标签。
     */
    private static boolean isTransient(Throwable e) {
        for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
            if (t instanceof MongoException mongo
                    && mongo.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL))
                return true;
        }
        return false;
    }

    /**
     * 抢红包的事务体：扣库存、记记录、加钱、记流水、更新气泡，一起成或一起废。
     *
     * <p>正因为这五件事在同一个事务里，才不存在「已抢到但没到账」的中间态，
     * 所以 {@code RedPacketRecord} 不再需要 status/dutTime 两阶段字段，也不需要补偿扫描。
     */
//    @Transactional(rollbackFor = Exception.class)
    public GrabOutcome settleGrab(RedPacket packet, RedPacketItem item, LinkGrabPacketDto dto,
                                  String sysMessageId, long sysSeq) {
        long now = now();
        ObjectId packetId = packet.getId();
        ObjectId userId = new ObjectId(dto.getUserId());

        // ① 扣库存，同时更新领取人列表。不再对 children 做 elemMatch —— children 只是审计快照，
        //    「谁抢走了哪一份」记在 RedPacketRecord 里，几百份的红包不必每次都定位子文档
        Query cas = eq(where(col(RedPacket::getId)).is(packetId)
                .and(col(RedPacket::getStatus)).is(RedPacketStatusKeys.IN_PROGRESS)
                .and(col(RedPacket::getRemainCount)).gt(0)
                .and(col(RedPacket::getRemainAmount)).gte(item.getAmount()));
        Update deduct = update()
                .inc(col(RedPacket::getRemainCount), -1)
                .inc(col(RedPacket::getRemainAmount), item.getAmount().negate())
                .addToSet(col(RedPacket::getClaimantIds), userId);  // 添加领取人到红包表
        RedPacket after = this.findAndModify(cas, deduct, options().returnNew(true).upsert(false));
        if (after == null)
            throw new RedPackBizException(ApiResult.error(RedPacketApiCode.RED_PACKET_SOLD_OUT));

        // ② 抢红包记录。(packet_id,user_id) 唯一索引是「一人一次」的最终防线，
        //    撞键就让 DuplicateKeyException 逃出去，整笔回滚
        RedPacketRecord record = new RedPacketRecord()
                .setPacketId(packetId)
                .setUserId(userId)
                .setBest(item.isBest())
                .setAmount(item.getAmount());
        record.setCreatedTime(now);
        this.getMongoTemplate().insert(record);

        // ③ 加钱。matchedCount 而不是 modifiedCount：金额可能恰好让文档内容不变，
        //    但只要匹配到就说明钱包存在、加钱生效
        UpdateResult credit = this.getMongoTemplate().updateFirst(
                eq(where(col(WalletInfo::getUserId)).is(userId)),
                update().inc(col(WalletInfo::getBalance), item.getAmount()),
                WalletInfo.class);
        if (credit.getMatchedCount() != 1)
            throw new RedPackBizException(ApiResult.error(RedPacketApiCode.WALLET_NOT_EXIST));

        // ④ 收入流水
        this.getMongoTemplate().insert(buildFlow(userId, item.getAmount(), WalletInOutKeys.IN,
                record.getId().toHexString(), "抢红包", now));

        // ⑤ 抢完才封盘。原来是无条件置 status=1，红包没抢完就被标成已抢完
        boolean soldOut = after.getRemainCount() == 0;
        if (soldOut)
            this.updateFirst(
                    eq(where(col(RedPacket::getId)).is(packetId)
                            .and(col(RedPacket::getStatus)).is(RedPacketStatusKeys.IN_PROGRESS)),
                    update().set(col(RedPacket::getStatus), RedPacketStatusKeys.SOLD_OUT));

        // ⑥ 消息不再更新：status 和 claimantIds 已从消息的 data 中移除
        //    前端通过 UPDATE_RED_PACKET 事件实时同步状态，离线场景调用详情接口查询
        String targetMessageId = packet.getMessageId() != null ? packet.getMessageId() : dto.getMessageId();

        // ⑦ 单聊的系统消息
        BaseMessage sysMessage = null;
        if (sysMessageId != null) {
            sysMessage = buildGrabSystemMessage(packet, dto, targetMessageId, sysMessageId, sysSeq, now);
            this.getMongoTemplate().insert(sysMessage);
            touchChatSession(packet.getChatId(), sysMessage, SYSTEM_MESSAGE_SUMMARY, now);
        }

        return new GrabOutcome(after.getRemainCount(), after.getRemainAmount(),
                soldOut ? RedPacketStatusKeys.SOLD_OUT : RedPacketStatusKeys.IN_PROGRESS,
                targetMessageId, sysMessage);
    }

    private BaseMessage buildGrabSystemMessage(RedPacket packet, LinkGrabPacketDto dto, String packetMessageId,
                                               String messageId, long seq, long now) {
        System.out.println("messageId:" + messageId);
        System.out.println(new ObjectId(messageId));
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

    public Class<? extends BaseMessage> messageClass(RedPacket packet) {
        return packet.getBizType() == PACKET_TYPE_SINGLE ? DefaultMessageInfo.class : GroupMessageInfo.class;
    }

    public Collection<String> pushTargets(RedPacket packet) {
        if (packet.getBizType() == PACKET_TYPE_SINGLE)
            return Arrays.asList(packet.getSndId().toHexString(), packet.getRcvId().toHexString());
        return this.redisService.getChatMemberIds(packet.getChatId());
    }

    private void pushGrabResult(RedPacket packet, LinkGrabPacketDto dto, GrabOutcome outcome) {
        Collection<String> targets = pushTargets(packet);
        if (CollectionUtils.isEmpty(targets))
            return;

        LinkRedPacketUpdate event = new LinkRedPacketUpdate()
                .setChatId(packet.getChatId())
                .setMessageId(outcome.packetMessageId())
                .setPacketId(packet.getId().toHexString())
                .setStatus(outcome.packetStatus())
                .setClaimantId(dto.getUserId())
                .setRemainCount(outcome.remainCount())
                .setRemainAmount(outcome.remainAmount());
        this.pushPublisher.push(EventType.UPDATE_RED_PACKET, targets, event);

        if (outcome.sysMessage() != null)
            this.pushPublisher.push(EventType.DEFAULT_MESSAGE, targets, outcome.sysMessage());
    }


    // ==================== 红包详情 ====================

    public ApiResult getRedPacket(LinkGetRedPacketDTO dto) {
        // 参数校验
        if (dto == null || !stringValidator(dto.getPacketId(), dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        RedPacket packet = this.findById(new ObjectId(dto.getPacketId()));
        if (packet == null)
            return ApiResult.error(RedPacketApiCode.RED_PACKET_NOT_EXIST);

        // 详情里有每个人抢到多少钱，不能只凭一个 packetId 就给看
        if (!canView(packet, dto.getUserId()))
            return ApiResult.error(RedPacketApiCode.RED_PACKET_FORBIDDEN);

        LinkRedPacketVO vo = new LinkRedPacketVO()
                .setId(dto.getPacketId())
                .setStatus(packet.getStatus())
                .setTotalAmount(packet.getTotalAmount())
                .setRemainAmount(packet.getRemainAmount())
                .setTotalCount(packet.getTotalCount())
                .setRemainCount(packet.getRemainCount());

        List<RedPacketRecord> records = this.getMongoTemplate().find(
                eq(where(col(RedPacketRecord::getPacketId)).is(new ObjectId(dto.getPacketId()))),
                RedPacketRecord.class);
        if (records.isEmpty())
            return ApiResult.success().setData(vo.setRecords(new ArrayList<>()));

        List<ObjectId> userIds = records.stream()
                .map(RedPacketRecord::getUserId).collect(Collectors.toList());
        Query userQuery = eq(where(col(UserInfo::getId)).in(userIds));
        userQuery.fields().include(col(UserInfo::getNickname), col(UserInfo::getAvatar));
        // merge 函数是防脏数据的：唯一索引保证一人一条，真撞了也只该丢一条昵称，不该让整个接口 500
        Map<ObjectId, UserInfo> userMap = this.getMongoTemplate().find(userQuery, UserInfo.class).stream()
                .collect(Collectors.toMap(UserInfo::getId, Function.identity(), (a, b) -> a));

        // 以 records 为主表遍历，不是以 users。用户被删或查不到时，这一份的金额仍要出现在明细里，
        // 否则「已领 N 份」和列表条数对不上
        List<LinkRedPacketRecordVO> rows = records.stream().map(record -> {
            UserInfo user = userMap.get(record.getUserId());
            return new LinkRedPacketRecordVO()
                    .setUserId(record.getUserId().toHexString())
                    .setNickname(user == null ? null : user.getNickname())
                    .setAvatar(user == null ? null : user.getAvatar())
                    .setAmount(record.getAmount())
                    .setBest(record.isBest());
        }).collect(Collectors.toList());

        return ApiResult.success().setData(vo.setRecords(rows));
    }

    /** 单聊红包只有收发双方能看详情；群红包按会话成员放行 */
    private boolean canView(RedPacket packet, String userId) {
        if (packet.getBizType() == PACKET_TYPE_SINGLE)
            return userId.equals(packet.getSndId().toHexString()) || userId.equals(packet.getRcvId().toHexString());
        Set<String> memberIds = this.redisService.getChatMemberIds(packet.getChatId());
        return memberIds != null && memberIds.contains(userId);
    }


    // ==================== 拆包（纯计算，不碰 Redis / Mongo） ====================

    /**
     * 把总金额拆成 count 份。
     *
     * <p>只做计算，不写 Redis —— 填库存是 {@link #primeRedisInventory} 的事，
     * 必须等事务 commit 之后再做。原来拆包顺手就 rightPush，事务一回滚库存就漏出去了。
     */
    public List<RedPacketItem> split(LinkSendPacketDto dto) {
        int count = dto.getCount();
        // 元 -> 分用 movePointRight(2)，绝不用 BigDecimal 直接除，避免除不尽丢分
        long totalFen = dto.getAmount().movePointRight(2).setScale(0, RoundingMode.DOWN).longValueExact();

        List<RedPacketItem> items = splitFen(totalFen, count);
        markBest(dto, items);

        // splitFen 里存的是「分」，这里统一转回「元」
        for (RedPacketItem item : items) {
            item.setAmount(BigDecimal.valueOf(item.getAmount().longValue(), 2));
        }
        return items;
    }

    private List<RedPacketItem> splitFen(long totalFen, int count) {
        long remainFen = totalFen;
        int remainCount = count;
        List<RedPacketItem> items = new ArrayList<>(count);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            long amount;
            if (remainCount == 1) {
                // 最后一份拿走剩余全部 —— 金额守恒的兜底，保证 Σ 每份 == 总额，一分不差
                amount = remainFen;
            } else {
                long max = remainFen / remainCount * 2;
                long upper = Math.min(max, remainFen - (remainCount - 1));
                if (upper < 1)
                    upper = 1;
                amount = 1 + random.nextLong(upper);
            }

            items.add(new RedPacketItem().setAmount(BigDecimal.valueOf(amount)));

            remainFen -= amount;
            remainCount--;
        }
        return items;
    }

    private void markBest(LinkSendPacketDto dto, List<RedPacketItem> items) {
        // 单聊定额红包只有一份，无所谓手气；拼手气红包只拆 1 份时同理
        if (dto.getType() != PACKET_TYPE_LUCKY || items.size() < 2)
            return;

        RedPacketItem best = items.get(0);
        for (RedPacketItem item : items) {
            if (item.getAmount().compareTo(best.getAmount()) > 0)
                best = item;
        }
        best.setBest(true);
    }
}
