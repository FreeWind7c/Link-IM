package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 被叫收到来电（振铃）。
 *
 * <p>纯统计事件，不改任何状态——被叫在名单里本来就是 INVITED。
 * 它的价值在排查：如果某人一直没振铃上报，说明他的腾讯云 IM 连接有问题
 * （我方 WebSocket 在线不代表 IM 在线，这是双长连接架构的固有风险）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcRingingHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.RINGING;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        log.info("群通话振铃 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
    }
}
