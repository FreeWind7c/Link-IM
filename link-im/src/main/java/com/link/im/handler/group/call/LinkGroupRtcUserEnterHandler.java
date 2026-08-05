package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 某人进入房间：把参与者置为 JOINED，并把整通通话置为「通话中」。
 *
 * <p><b>这是判定「真正加入通话」的权威事件</b>，而不是 ACCEPT——
 * ACCEPT 只代表点了接听按钮，进房才代表媒体通道真正建立。
 * 用户点了接听但因权限/网络失败进不了房的情况是存在的。
 *
 * <p>群内每个人都会收到并上报同一个人的进房事件，因此这里会收到 N 份副本。
 * 基类的条件更新（只允许 INVITED → JOINED）天然幂等，重复的会无副作用落空。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcUserEnterHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.USER_ENTER;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        // 通话中被拉进来的人可能不在原始名单里，先补进去
        ensureParticipant(call, call.getTargetId(), CallParticipant.INVITED);

        boolean changed = advanceParticipant(call, CallParticipant.JOINED);
        if (changed) {
            // 首个进房者把通话推进到「通话中」（条件更新，重复调用无副作用）
            markConnected(call);
            log.info("群通话进房 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
        }
    }
}
