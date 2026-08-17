package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本端通话结束（接通后挂断）：把自己置为 LEFT，并检查是否该结算。
 *
 * <p>与 USER_LEAVE 的区别在视角：这是第一人称（我挂断了），
 * USER_LEAVE 是第三人称（我看到某人离开了）。同一次离开通常两种事件都会到，
 * 谁先到谁生效。
 *
 * <p>客户端上报的 duration 这里<b>不直接采信</b>——通话时长由服务端用
 * end_time - start_time 算，两端都是服务端时间戳，比客户端时钟可靠。
 * duration 仅在日志里留痕，便于排查客户端与服务端计时不一致的问题。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcHangUpHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.HANG_UP;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        print("通话结束",call);
        boolean changed = advanceParticipant(call, CallParticipant.LEFT);
        if (changed) {
            log.info("群通话挂断 -> messageId={} user={} 客户端计时={}s",
                    call.getMessageId(), call.getTargetId(), call.getDuration());
        }
        settleIfFinished(call);
    }
}
