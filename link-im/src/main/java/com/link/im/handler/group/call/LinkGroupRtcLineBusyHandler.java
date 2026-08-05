package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 某人忙线（正在另一通话中）：置为 LINE_BUSY，并检查是否该结算。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcLineBusyHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.LINE_BUSY;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        boolean changed = advanceParticipant(call, CallParticipant.LINE_BUSY);
        if (changed) {
            log.info("群通话忙线 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
        }
        settleIfFinished(call);
    }
}
