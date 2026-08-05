package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通话中邀请了新成员：把新成员补进参与者名单。
 *
 * <p>通话内所有人都会收到这个事件并各自上报，所以会有多份副本。
 * ensureParticipant 内部先查后插，重复的会被挡掉。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcInviteHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.INVITE;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        ensureParticipant(call, call.getTargetId(), CallParticipant.INVITED);
        log.info("群通话邀请 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
    }
}
