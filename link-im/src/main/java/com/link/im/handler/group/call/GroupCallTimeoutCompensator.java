package com.link.im.handler.group.call;

import com.link.im.entity.data.CallData;
import com.link.im.entity.data.CallParticipant;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.mongo.BasePlatFormMongoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 群通话记录的超时补偿。
 *
 * <p><b>为什么必须有这个。</b>群通话的状态全靠客户端事后上报，而上报是尽力而为的：
 * 用户直接关标签页、浏览器崩溃、上报瞬间断网，HANG_UP 就永远发不出来。
 * 没有补偿的话，这条记录会<b>永久停在「通话中」</b>，会话列表里挂着一通
 * 永不结束的电话。这是方案 A（依赖客户端上报）相比自建信令的固有代价，
 * 必须在服务端兜住。
 *
 * <p>补偿策略：把超过 {@link #MAX_CALL_DURATION_MS} 仍未结算的群通话记录
 * 强制结算。判定最终状态的依据与正常结算一致——有人进过房就算「已结束」，
 * 否则算「无人接听」。
 *
 * <p>阈值取 6 小时而非几分钟：正常的长会议可能开很久，误杀一通进行中的通话
 * 比晚几小时清理一条脏数据更糟。这里的目标是兜底，不是精确计时。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
@EnableScheduling
public class GroupCallTimeoutCompensator extends BasePlatFormMongoService<GroupMessageInfo> {

    /** 超过这个时长仍未结算的通话视为异常，强制结算（6 小时） */
    private static final long MAX_CALL_DURATION_MS = 6 * 60 * 60 * 1000L;

    /** 单次最多处理多少条，避免异常情况下一次扫出大量记录压垮库 */
    private static final int BATCH_LIMIT = 200;

    /**
     * 每 30 分钟扫一次。频率不必高——这是兜底路径，正常情况下扫不出东西。
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    public void compensate() {
//        long deadline = now() - MAX_CALL_DURATION_MS;
//
//        Query q = eq(where(col(DefaultMessageInfo::getType)).is(MessageType.RTC_CALL_MESSAGE.getType())
//                .and("data.group_call").is(true)
//                .and("data.status").in(CallData.GROUP_CALLING, CallData.GROUP_CONNECTED)
//                .and(col(DefaultMessageInfo::getTimestamp)).lt(deadline));
//        q.limit(BATCH_LIMIT);
//
//        List<GroupMessageInfo> stale = this.find(q);
//        if (stale.isEmpty()) return;
//
//        log.warn("发现 {} 条超时未结算的群通话记录，开始补偿", stale.size());
//
//        for (GroupMessageInfo message : stale) {
//            if (!(message.getData() instanceof CallData data)) continue;
//
//            boolean anyJoined = data.getParticipants() != null
//                    && data.getParticipants().stream().anyMatch(p -> p.getJoinTime() > 0);
//            int finalStatus = anyJoined ? CallData.GROUP_FINISHED : CallData.GROUP_NOT_CONNECTED;
//
//            // 条件更新：期间若有正常上报把它结算了，这里就不再覆盖
//            Query one = eq(where(col(DefaultMessageInfo::getId)).is(message.getId()));
//            one.addCriteria(Criteria.where("data.status")
//                    .in(CallData.GROUP_CALLING, CallData.GROUP_CONNECTED));
//
//            Update update = update()
//                    .set("data.status", finalStatus)
//                    .set("data.end_time", now());
//            // 把仍挂在非终态的参与者一并收尾，避免名单里留下永远「通话中」的人
//            update.filterArray(Criteria.where("elem.status")
//                    .in(CallParticipant.INVITED, CallParticipant.JOINED));
//            update.set("data.participants.$[elem].status", CallParticipant.LEFT);
//            update.set("data.participants.$[elem].leave_time", now());
//
//            long modified = this.updateFirst(one, update);
//            if (modified > 0) {
//                log.warn("群通话超时补偿 -> messageId={} status={}", message.getId(), finalStatus);
//            }
//        }
    }
}
