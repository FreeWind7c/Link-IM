package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 某人超时未接听：置为 NO_RESPONSE，并检查是否该结算。
 *
 * <p>前端对 TRTC 的 userIDList（超时是批量结算的）做了拆分，
 * 每个超时用户单独上报一条，所以这里按单人处理。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcNoResponseHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.NO_RESPONSE;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        boolean changed = advanceParticipant(call, CallParticipant.NO_RESPONSE);
        if (changed) {
            log.info("群通话未接听 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
        }
        settleIfFinished(call);
    }
}
