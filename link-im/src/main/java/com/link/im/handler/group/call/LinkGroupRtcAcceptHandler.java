package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 某人点了接听。
 *
 * <p><b>刻意不改参与者状态</b>：接听只代表点了按钮，此时媒体通道还没建立，
 * 用户可能因摄像头权限被拒、网络失败而最终进不了房。真正的「加入通话」
 * 以 USER_ENTER（进房）为准，见 {@link LinkGroupRtcUserEnterHandler}。
 *
 * <p>保留这个 handler 是为了让事件链路完整、便于排查（日志里能看到
 * 「点了接听但没进房」这种异常），以及将来做接听率统计时有数据可用。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcAcceptHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.ACCEPT;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        log.info("群通话接听 -> messageId={} user={}", call.getMessageId(), call.getTargetId());
    }
}
