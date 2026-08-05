package com.link.core.event.handler;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import io.netty.channel.Channel;

/**
 * 群通话（TRTC）状态同步事件处理器。
 *
 * <p>与 {@link RTCEventHandler} 并行的一套：那套处理自建 WebRTC 的信令转发，
 * 这套处理 TRTC 的事后状态上报。分开是因为二者语义不同——
 * 前者「转发即事实」，后者「事实已发生，只管记账」。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
public interface GroupRTCEventHandler {

    GroupRTCEventType event();

    void handler(LinkRtcGroupCall call, Channel channel);
}
