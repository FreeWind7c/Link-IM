package com.link.im.handler.group.call;

import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.config.LinkCoreConfig;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.data.CallData;
import com.link.im.entity.data.CallParticipant;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.service.LinkRedisService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;
import java.util.Set;

/**
 * 群通话同步 handler 的公共基类：收拢「定位记录 / 推进参与者状态 / 结算通话 / 推送」
 * 这几件每个 handler 都要做的事。
 *
 * <p><b>幂等是这里的第一原则。</b>群通话的上报天然会重复——USER_ENTER 这类事件
 * 群里每个人都会收到并各自上报一份，服务端会收到同一事实的 N 份副本。
 * 因此所有状态更新都写成「条件更新」：把期望的前置状态写进 Query，
 * 靠 MongoDB 的原子性天然去重，重复上报会因条件不匹配而无副作用地落空，
 * 不需要额外的去重表。
 *
 * <p><b>状态只能单向前进。</b>上报还可能乱序到达（不同客户端网络延迟不同），
 * 所以推进参与者状态时用状态优先级卡住：已经 JOINED 的人不会被迟到的
 * RINGING 打回 INVITED。见 {@link #advanceParticipant}。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
public abstract class BaseGroupCallHandler extends BasePlatFormMongoService<GroupMessageInfo> {

    @Autowired
    protected LinkCoreConfig config;

    @Autowired
    protected LinkRedisService redisService;



    protected Query locate(LinkRtcGroupCall call) {
        if (notEmpty(call.getMessageId())) {
            return eq(where(col(DefaultMessageInfo::getId)).is(call.getMessageId()));
        }
        if (notEmpty(call.getCallId())) {
            return eq(where("data.call_id").is(call.getCallId()));
        }
        return null;
    }

    protected DefaultMessageInfo findCall(LinkRtcGroupCall call) {
        Query q = locate(call);
        if (q == null) return null;
        return this.getMongoTemplate().findOne(q, DefaultMessageInfo.class);
    }


    /**
     * 把某个参与者的状态向前推进到 {@code toStatus}，并按需写入时间戳。
     *
     * <p>用「状态优先级」做条件更新实现幂等 + 防乱序回退：只有当该参与者
     * 当前状态的优先级<b>低于</b>目标状态时才更新。这样：
     * <ul>
     *   <li>重复上报（同一状态）→ 条件不满足，无副作用；</li>
     *   <li>乱序上报（迟到的旧状态）→ 条件不满足，不会覆盖新状态。</li>
     * </ul>
     *
     * <p>用 MongoDB 的 arrayFilters 定位数组元素，整个操作在一次原子写里完成，
     * 不需要「先读再写」，因此并发上报也安全。
     *
     * @param call      上报体
     * @param toStatus  目标状态，见 {@link CallParticipant} 的常量
     * @return 是否真正发生了更新（false 表示重复/乱序上报被挡下）
     */
    protected boolean advanceParticipant(LinkRtcGroupCall call, int toStatus) {
        Query q = locate(call);
        if (q == null) return false;

        List<Integer> from = lowerPriorityStatuses(toStatus);
        if (from.isEmpty()) return false;

        // 前置条件：该用户当前处于「优先级更低」的状态。
        // elemMatch 保证这两个条件命中的是同一个数组元素——分开写会变成
        // 「存在某人 user_id 匹配」且「存在某人状态匹配」，可能是两个不同的人。
        q.addCriteria(Criteria.where("data.participants").elemMatch(
                Criteria.where("user_id").is(call.getTargetId())
                        .and("status").in(from)
        ));

        Update update = update().set("data.participants.$[elem].status", toStatus);
        if (toStatus == CallParticipant.JOINED) {
            update.set("data.participants.$[elem].join_time", now());
        } else if (CallParticipant.isFinal(toStatus)) {
            update.set("data.participants.$[elem].leave_time", now());
        }
        // arrayFilter 必须和上面的 elemMatch 带同样的状态条件，否则会错更新：
        // 群里只要还有别人处于低优先级状态，elemMatch 就会放行，
        // 而只按 user_id 过滤的 arrayFilter 会把目标用户已有的高优先级状态覆盖掉
        // （例如已 JOINED 的人被一条迟到的 REJECT 改写）。
        update.filterArray(Criteria.where("elem.user_id").is(call.getTargetId())
                .and("elem.status").in(from));

        long modified = this.updateFirst(q, update);
        return modified > 0;
    }

    /**
     * 列出优先级低于目标状态的所有状态——即「允许被推进到目标状态」的来源状态。
     *
     * <p>优先级：INVITED(0) &lt; JOINED(1) &lt; 各终态(LEFT/REJECTED/NO_RESPONSE/LINE_BUSY)。
     * 终态之间互不覆盖：先落地的终态胜出（比如已经 REJECTED 的人不会被迟到的
     * NO_RESPONSE 改写，因为拒接是更明确的用户行为）。
     */
    private List<Integer> lowerPriorityStatuses(int toStatus) {
        if (toStatus == CallParticipant.JOINED) {
            return List.of(CallParticipant.INVITED);
        }
        if (CallParticipant.isFinal(toStatus)) {
            // 终态可以从「未应答」或「已加入」推进而来，但不能覆盖已有的终态
            return List.of(CallParticipant.INVITED, CallParticipant.JOINED);
        }
        // 推进到 INVITED 本身没有意义（那是初始状态）
        return List.of();
    }

    /**
     * 确保参与者在名单里。用于 INVITE（通话中拉人）和降级场景
     * （被叫的上报先于主叫的 CALL 到达，名单里还没有这个人）。
     *
     * <p>用「查询条件里排除已存在」+ push 合成一次原子写：
     * Query 带上 {@code data.participants.user_id != userId}，
     * 已存在时条件不满足，push 不会执行。这样避免了「先查再写」
     * 两步之间的并发窗口——多份重复上报同时到达也只会插入一条。
     */
    protected void ensureParticipant(LinkRtcGroupCall call, String userId, int status) {
        Query q = locate(call);
        if (q == null || userId == null || userId.isEmpty()) return;

        // 名单里还没有这个人时才 push，条件与写入在同一条命令里，天然幂等
        q.addCriteria(Criteria.where("data.participants.user_id").ne(userId));

        CallParticipant p = new CallParticipant()
                .setUserId(userId)
                .setStatus(status)
                .setInitiator(false);
        this.updateFirst(q, update().push("data.participants", p));
    }

    protected void settleIfFinished(LinkRtcGroupCall call) {
        DefaultMessageInfo message = findCall(call);

        if (message == null) return;
        if (!(message.getBaseData() instanceof CallData data)) return;
        if (!data.isGroupCall()) return;

        List<CallParticipant> participants = data.getParticipants();
        if (participants == null || participants.isEmpty()) return;

        // 还有人在通话里 → 不结算
        boolean allFinal = participants.stream()
                .allMatch(p -> CallParticipant.isFinal(p.getStatus()));
        if (!allFinal) return;

        boolean anyJoined = participants.stream().anyMatch(p -> p.getJoinTime() > 0);
        int finalStatus = anyJoined ? CallData.GROUP_FINISHED : CallData.GROUP_NOT_CONNECTED;

        // 条件更新：只结算尚未结算的记录，避免并发重复结算
        Query q = locate(call);
        if (q == null) return;
        q.addCriteria(Criteria.where("data.status")
                .in(CallData.GROUP_CALLING, CallData.GROUP_CONNECTED));

        Update update = update()
                .set("data.status", finalStatus)
                .set("data.end_time", now());

        FindAndModifyOptions options = options();
        options.returnNew(true);
        options.upsert(false);
        GroupMessageInfo settled = this.findAndModify(q, update, options);
        if (settled == null) {
            // 已被别的上报结算过，正常情况，不必告警
            return;
        }

        log.info("群通话结束 -> messageId={} status={}", settled.getId(), finalStatus);
        updateChatSession(settled);
        pushToMembers(settled);
    }

    protected void updateChatSession(GroupMessageInfo message) {
        if (message.getChatId() == null) return;
        Query eq = eq(where(col(ChatSession::getChatId)).is(message.getChatId()));
        Update update = update()
                .set(col(ChatSession::getLastMsgSummary),
                        MessageType.summaryOf(message.getType(), message.getBaseData()))
                .set(col(ChatSession::getLastMsgType), message.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), message.getSeq());
        this.getMongoTemplate().updateFirst(eq, update, ChatSession.class);
    }

    protected void pushToMembers(GroupMessageInfo message) {
        if (message.getChatId() == null) return;
        Set<String> memberIds = this.redisService.getChatMemberIds(message.getChatId());
        if (memberIds == null || memberIds.isEmpty()) return;

        for (String memberId : memberIds) {
            List<Channel> channels = this.config.getSessionManager().getChannel(memberId);
            if (channels == null || channels.isEmpty()) continue;
            this.config.getLinkSender().send(EventType.DEFAULT_MESSAGE, channels, message);
        }
    }

    /**
     * 把通话记录置为「通话中」。首个参与者进房时调用。
     * 条件更新：只有还在 CALLING 的记录才推进，已结束的不会被拉回。
     */
    protected void markConnected(LinkRtcGroupCall call) {
        Query q = locate(call);
        if (q == null) return;
        q.addCriteria(Criteria.where("data.status").is(CallData.GROUP_CALLING));
        this.updateFirst(q, update()
                .set("data.status", CallData.GROUP_CONNECTED)
                .set("data.start_time", now()));
    }

    protected boolean notEmpty(String s) {
        return s != null && !s.isEmpty();
    }
}
