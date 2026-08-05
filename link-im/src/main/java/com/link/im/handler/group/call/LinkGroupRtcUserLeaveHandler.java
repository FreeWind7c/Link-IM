package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 某人离开房间：置为 LEFT，并检查是否该结算整通通话。
 *
 * <p>与 HANG_UP 的区别：HANG_UP 是「本端自己挂断」（第一人称，带时长），
 * USER_LEAVE 是「观察到别人离开」（第三人称）。两者都会把人置为 LEFT，
 * 谁先到谁生效，后到的因条件不满足而落空——这正是幂等设计的价值。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcUserLeaveHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.USER_LEAVE;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        boolean changed = advanceParticipant(call, CallParticipant.LEFT);
        if (changed) {
            log.info("群通话离开 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
        }
        // 无论本次是否真正更新，都检查一次结算：
        // 可能是别人的上报先把最后一个人置为终态，本次负责触发结算
        settleIfFinished(call);
    }
}
