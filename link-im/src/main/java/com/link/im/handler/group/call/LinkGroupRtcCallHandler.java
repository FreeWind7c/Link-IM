package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.entity.data.CallData;
import com.link.im.entity.data.CallParticipant;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 主叫发起群通话：建立通话记录。整条链路的起点。
 *
 * <p>只有主叫会发这个事件，所以不像其他事件那样会收到多份副本。
 * 但仍需防重：主叫端网络抖动重发、或用户快速点两次，都可能重复到达。
 * 这里靠 seq 分配器的 duplicate 标识 + 主键冲突双重兜底。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcCallHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Autowired
    private MessageSeqAllocator seqAllocator;

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.CALL;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        // CALL 必须带 messageId：它是后续所有上报的关联键，没有就没法建记录
        if (!notEmpty(call.getMessageId()) || !notEmpty(call.getChatId())) {
            log.warn("群通话发起缺少 messageId/chatId，丢弃 -> sndId={}", call.getSndId());
            return;
        }

        // 分配会话内递增 seq，同时天然防重（同一 messageId 第二次会返回 duplicate）
        MessageSeqAllocator.SeqResult allocate =
                this.seqAllocator.allocate(call.getChatId(), call.getMessageId());
        if (allocate.duplicate()) {
            log.info("群通话重复发起，忽略 -> messageId={}", call.getMessageId());
            return;
        }

        GroupMessageInfo message = (GroupMessageInfo) new GroupMessageInfo()
                .setId(call.getMessageId())
                .setType(MessageType.RTC_CALL_MESSAGE.getType())
                .setSndId(call.getSndId())
                // 群通话没有单一接收者，rcvId 留空，接收范围由 chatId 决定
                .setRcvId(null)
                .setChatId(call.getChatId())
                .setState(1)
                .setData(new CallData()
                        .setMediaType(call.getMediaType())
                        .setStatus(CallData.GROUP_CALLING)
                        // 呼叫发起时尚未接通，start_time 等首个参与者进房时再写
                        .setStartTime(0)
                        .setEndTime(0)
                        .setGroupCall(true)
                        .setRoomId(call.getRoomId())
                        .setCallId(call.getCallId())
                        .setParticipants(buildParticipants(call)))
                .setTimestamp(now());
        message.setSeq((int) allocate.seq());

        try {
            this.insert(message);
        } catch (DuplicateKeyException e) {
            // seq 分配器与插入之间的窗口内并发重发，忽略即可
            log.info("群通话记录已存在，忽略 -> messageId={}", call.getMessageId());
            return;
        }

        log.info("群通话发起 -> messageId={} chatId={} 参与者={}",
                call.getMessageId(), call.getChatId(),
                message.getData() instanceof CallData d ? d.getParticipants().size() : 0);

        updateChatSession(message);
        pushToMembers(message);
    }

    /**
     * 构建参与者名单。主叫标记为 initiator 且直接置为 JOINED——
     * 发起方本来就在通话里，不需要「接听」。其余人初始为 INVITED（振铃中）。
     */
    private List<CallParticipant> buildParticipants(LinkRtcGroupCall call) {
        List<CallParticipant> participants = new ArrayList<>();

        participants.add(new CallParticipant()
                .setUserId(call.getSndId())
                .setStatus(CallParticipant.JOINED)
                .setJoinTime(now())
                .setInitiator(true));

        List<String> userIds = call.getUserIds();
        if (userIds == null) return participants;

        // 去重并排除主叫自己（前端已处理，这里再兜一道，避免名单里出现重复元素）
        for (String userId : new LinkedHashSet<>(userIds)) {
            if (userId == null || userId.isEmpty()) continue;
            if (userId.equals(call.getSndId())) continue;
            participants.add(new CallParticipant()
                    .setUserId(userId)
                    .setStatus(CallParticipant.INVITED)
                    .setInitiator(false));
        }
        return participants;
    }
}
