package com.link.core.event.handler;

import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import io.netty.channel.Channel;


/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
public interface RTCEventHandler {


    public RTCEventType event();
    public void handler(LinkRtcCall call, Channel channel);


}
