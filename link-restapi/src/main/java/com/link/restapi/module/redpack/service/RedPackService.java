package com.link.restapi.module.redpack.service;

import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.core.event.EventType;
import com.link.common.redis.RedisKeys;
import com.link.base.seq.MessageSeqAllocator;
import com.link.common.constants.redpack.RedPacketStatusKeys;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.data.RedPacketData;
import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.redpack.RedPacket;
import com.link.base.entity.redpack.RedPacketItem;
import com.link.base.entity.redpack.RedPacketRecord;
import com.link.base.entity.user.UserInfo;
import com.link.base.entity.wallet.WalletInfo;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.enums.redpack.RedPacketApiCode;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.manager.CacheDataManager;
import com.link.restapi.module.redpack.model.vo.RedPacketRemainInfoVO;
import com.link.restapi.utils.ApiResult;
import com.link.base.vo.base.BaseMessageVO;
import com.link.restapi.module.redpack.exception.RedPackBizException;
import com.link.restapi.module.redpack.model.dto.LinkGetRedPacketDTO;
import com.link.restapi.module.redpack.model.dto.LinkGrabPacketDto;
import com.link.restapi.module.redpack.model.dto.LinkSendPacketDto;
import com.link.restapi.module.redpack.model.vo.LinkRedPacketRecordVO;
import com.link.restapi.module.redpack.model.vo.LinkRedPacketVO;
import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Query;
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

    private static final int PACKET_TYPE_SINGLE = 1;

    private static final int PACKET_TYPE_LUCKY = 2;

    private static final String IDEMPOTENT_FLAG = "1";
    private static final long IDEMPOTENT_TTL_HOURS = 24L;

    private static final long INVENTORY_TTL_HOURS = 25L;

    private static final int MAX_COUNT = 200;

    /** 单个红包金额上限 */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("20000");

    /** 抢红包锁：最多等 3 秒，拿不到就让客户端重试，别把 HTTP 线程挂在这儿 */
    private static final long GRAB_LOCK_WAIT_SECONDS = 3L;

    /** 抢红包锁租期：兜底防止持锁进程崩了把红包锁死 */
    private static final long GRAB_LOCK_LEASE_SECONDS = 30L;

    private static final Pattern BIZ_DETAIL_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Autowired
    private CacheDataManager cacheManager;

    @Autowired
    private BasePlatFormRedisService redisService;

    @Autowired
    private RedPacketTxExecutorService tx;

    public ApiResult sendRedPacket(LinkSendPacketDto dto) {
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


        if (!isValidBizDetailId(dto.getBizDetailId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String idemKey = RedisKeys.RED_PACKET_SEND_IDEM + dto.getBizDetailId();
        if (!Boolean.TRUE.equals(redisService.opsForValue()
                .setIfAbsent(idemKey, IDEMPOTENT_FLAG, IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS)))
            return ApiResult.error(RedPacketApiCode.RED_PACKET_DUPLICATE_SUBMIT);

        long now = now();
        String packetId = new ObjectId().toHexString();
        try {
            String rcvId = resolveSendTarget(dto);
            List<RedPacketItem> items = split(dto);

            String messageId = new ObjectId().toHexString();
            long seq = this.messageSeqAllocator.allocate(dto.getChatId(), messageId).seq();

            RedPacketTxExecutorService.SendOutcome outcome = tx.doSend(dto, packetId, rcvId, messageId, seq, now);

            primeRedisInventory(packetId, items);
            pushRedPacketMessage(dto, outcome.getMessage());

            return ApiResult.success().setData(outcome.getPacket());

        } catch (RedPackBizException e) {
            // 业务分支：事务整体回滚了，钱没扣红包没建，放行同一个 bizDetailId 重试
            redisService.deleteKey(idemKey);
            return e.getResult();
        } catch (DuplicateKeyException e) {
            // 撞 biz_detail_id 唯一索引 = 同一次点击的重复请求（Redis 幂等键失效时的兜底）。
            // 前一单已经成了，这里绝不能删幂等键——删了就是放行第二次扣款
            log.warn("发红包重复提交 bizDetailId={}, sndId={}", dto.getBizDetailId(), dto.getSndId());
            return ApiResult.error(RedPacketApiCode.RED_PACKET_DUPLICATE_SUBMIT);
        } catch (Exception e) {
            redisService.deleteKey(idemKey);
            log.error("发红包失败 packetId={}, chatId={}, sndId={}", packetId, dto.getChatId(), dto.getSndId(), e);
            return ApiResult.error(RedPacketApiCode.RED_PACKET_CREATE_FAILED);
        }
    }

    public ApiResult grabRedPacket(LinkGrabPacketDto dto) {
        if (dto == null || !stringValidator(dto.getUserId(), dto.getPacketId())
                || !ObjectId.isValid(dto.getUserId()) || !ObjectId.isValid(dto.getPacketId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String suffix = dto.getPacketId() + ":" + dto.getUserId();
        String idemKey = RedisKeys.RED_PACKET_GRAB_IDEM + suffix;
        String inventoryKey = RedisKeys.RED_PACKET + dto.getPacketId();
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
            if (!Boolean.TRUE.equals(redisService.opsForValue()
                    .setIfAbsent(idemKey, IDEMPOTENT_FLAG, IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS)))
                return ApiResult.error(RedPacketApiCode.RED_PACKET_REPEAT_GRAB);


            boolean exists = this.getMongoTemplate().exists(eq(where(col(WalletInfo::getUserId)).is(new ObjectId(dto.getUserId()))), WalletInfo.class);
            if (!exists)
                return ApiResult.error(RedPacketApiCode.WALLET_NOT_EXIST);

            idemHeld = true;
            item = (RedPacketItem) redisService.popListRight(inventoryKey);
            if (item == null)
                return ApiResult.error(RedPacketApiCode.RED_PACKET_SOLD_OUT);


            boolean single = packet.getBizType() == PACKET_TYPE_SINGLE;
            String sysMessageId = new ObjectId().toHexString();
            long sysSeq = single ? this.messageSeqAllocator.allocate(packet.getChatId(), sysMessageId).seq() : 0L;

            RedPacketTxExecutorService.GrabOutcome outcome;
            try {
                outcome = tx.settleGrab(packet, item, dto, sysMessageId, sysSeq);
                if (outcome.isSoldOut()) {
                    List<RedPacketRecord> allRecords = this.getPrimaryMongoTemplate().find(
                            eq(where(col(RedPacketRecord::getPacketId)).is(packet.getId())),
                            RedPacketRecord.class);

                    List<ObjectId> claimantIds = allRecords.stream()
                            .map(RedPacketRecord::getUserId)
                            .collect(Collectors.toList());

                    System.out.println("claimants:" + claimantIds.stream().map(v -> {
                        return v.toHexString();
                    }).collect(Collectors.toList()).toString());
                    this.getMongoTemplate().updateFirst(
                            eq(where(col(RedPacket::getId)).is(packet.getId())
                                    .and(col(RedPacket::getStatus)).is(RedPacketStatusKeys.IN_PROGRESS)),
                            update()
                                    .set(col(RedPacket::getStatus), RedPacketStatusKeys.SOLD_OUT)
                                    .set(col(RedPacket::getRemainCount), 0)
                                    .set(col(RedPacket::getRemainAmount), BigDecimal.ZERO)
                                    .set(col(RedPacket::getClaimantIds), claimantIds),RedPacket.class);
                }
                redisService.addSet(RedisKeys.RED_PACKET_CLAIMANT+dto.getPacketId(),dto.getUserId());
                redisService.expireKey(RedisKeys.RED_PACKET_CLAIMANT+dto.getPacketId(), 24, TimeUnit.HOURS);
            } catch (DuplicateKeyException e) {
                keepIdem = true;
                return ApiResult.error(RedPacketApiCode.RED_PACKET_REPEAT_GRAB);
            }
            committed = true;



            // 事务外后置：commit 之后才推
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
                    redisService.addListRight(inventoryKey, item);
                // 事务没提交 = 这次不算抢过，放行重试。「已抢过」和「重复提交」两种情况除外
                if (idemHeld && !keepIdem)
                    redisService.deleteKey(idemKey);
            }
            if (locked && lock.isHeldByCurrentThread())
                lock.unlock();
        }
    }

    private static boolean isValidBizDetailId(String bizDetailId) {
        return bizDetailId != null && BIZ_DETAIL_ID_PATTERN.matcher(bizDetailId).matches();
    }

    private String resolveSendTarget(LinkSendPacketDto dto) {
        if (dto.getType() == PACKET_TYPE_LUCKY) {

            Set<String> memberIds = this.cacheManager.getGroupMemberId(dto.getRcvId());
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

    private void primeRedisInventory(String packetId, List<RedPacketItem> items) {
        String key = RedisKeys.RED_PACKET + packetId;
        redisService.listRightPushAll(key,items.toArray());
        redisService.expireKey(key, INVENTORY_TTL_HOURS, TimeUnit.HOURS);
    }

    private void pushRedPacketMessage(LinkSendPacketDto dto, BaseMessage message) {

        if (dto.getType() == PACKET_TYPE_LUCKY) {
            Set<String> memberIds = this.cacheManager.getGroupMemberId(dto.getRcvId());
            if (CollectionUtils.isEmpty(memberIds))
                return;
            this.pushPublisher.push(EventType.GROUP_MESSAGE, memberIds, message);
        } else {
            this.pushPublisher.push(EventType.DEFAULT_MESSAGE,
                    Arrays.asList(dto.getSndId(), message.getRcvId().toHexString()), message);
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
        Set<String> memberIds = this.cacheManager.getGroupMemberId(packet.getRcvId().toHexString());
        return memberIds != null && memberIds.contains(userId);
    }

    public Class<? extends BaseMessage> messageClass(RedPacket packet) {
        return packet.getBizType() == PACKET_TYPE_SINGLE ? DefaultMessageInfo.class : GroupMessageInfo.class;
    }

    public Collection<String> pushTargets(RedPacket packet) {
        if (packet.getBizType() == PACKET_TYPE_SINGLE)
            return Arrays.asList(packet.getSndId().toHexString(), packet.getRcvId().toHexString());
        return this.cacheManager.getGroupMemberId(packet.getRcvId().toHexString());
    }

    private void pushGrabResult(RedPacket packet, LinkGrabPacketDto dto, RedPacketTxExecutorService.GrabOutcome outcome) {
        Collection<String> targets = pushTargets(packet);
        if (CollectionUtils.isEmpty(targets))
            return;

        Set<String> claimants = redisService.getSetMembers(RedisKeys.RED_PACKET_CLAIMANT+dto.getPacketId());

        packet.setStatus(outcome.getPacketStatus());
        BaseMessageVO vo = new BaseMessageVO();
        RedPacketData data = RedPacketData.toData(packet,claimants);
        if (packet.getBizType() == 1)
            vo = this.getMongoTemplate().findById(packet.getMessageId(), DefaultMessageInfo.class).toVo();
        else
            vo = this.getMongoTemplate().findById(packet.getMessageId(), GroupMessageInfo.class).toVo();
        vo.setData(data.toJson()).setBaseData(data);

        this.pushPublisher.push(EventType.UPDATE_MESSAGE, targets, vo);

        if (outcome.getSysMessage() != null)
            this.pushPublisher.push(EventType.DEFAULT_MESSAGE, targets, outcome.getSysMessage());
    }

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

        // 使用 LinkRedisService 获取剩余信息（带缓存一致性）
        RedPacketRemainInfoVO remainInfo =
            getRedPacketRemainInfo(dto.getPacketId(), packet);

        LinkRedPacketVO vo = new LinkRedPacketVO()
                .setId(dto.getPacketId())
                .setStatus(packet.getStatus())
                .setTotalAmount(packet.getTotalAmount())
                .setTotalCount(packet.getTotalCount())
                .setRemainAmount(remainInfo.getRemainAmount())
                .setRemainCount(remainInfo.getRemainCount());

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
        Set<String> memberIds = this.cacheManager.getGroupMemberId(packet.getRcvId().toHexString());
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

    public RedPacketRemainInfoVO getRedPacketRemainInfo(String packetId, RedPacket packet) {
        Long remainCount = redisService.getListSize(RedisKeys.RED_PACKET + packetId);

        RedPacketRemainInfoVO info = new RedPacketRemainInfoVO();
        info.setRemainCount(remainCount != null ? remainCount.intValue() : 0);
        info.setRemainAmount(packet.getTotalAmount());
        return info;
    }

}
