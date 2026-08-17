package com.link.im.handler.group.call;

import com.google.gson.Gson;
import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 某人拒接：置为 REJECTED，并检查是否该结算。
 *
 * <p>群通话里所有被邀请者都会收到别人的拒接事件（TRTC 的群组语义），
 * 所以同一次拒接会有多份上报，靠基类的条件更新去重。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcRejectHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.REJECT;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        log.info("被动取消：" + new Gson().toJson(call));
        boolean changed = advanceParticipant(call, CallParticipant.REJECTED);
        if (changed) {
            log.info("群通话拒接 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
        }
        settleIfFinished(call);
    }
}
