package com.link.im.handler.call;

import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.RTCEventHandler;
import io.netty.channel.Channel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Component
public class LinkRtcOfferEventHandler implements RTCEventHandler {
    @Autowired
    private LinkCoreConfig config;

    @Override
    public RTCEventType event() {
        return RTCEventType.OFFER;
    }

    @Override
    public void handler(LinkRtcCall call,Channel channel) {
        List<Channel> channels = this.config.getSessionManager().getChannel(call.getRcvId());
        if (channels == null || channels.isEmpty())
            return;
        this.config.getLinkSender().send(EventType.RTC_CALL,channels,call);
    }
}
