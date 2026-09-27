package com.link.dispatcher.handler.call;

import com.google.gson.Gson;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.RTCEventHandler;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.mongo.BasePlatFormMongoService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Slf4j
@Component
public class LinkRtcCancelEventHandler extends BasePlatFormMongoService<DefaultMessageInfo> implements RTCEventHandler {

    @Autowired
    private LinkCoreConfig config;

    @Override
    public RTCEventType event() {
        return RTCEventType.CANCEL;
    }

    @Override
    public void handler(LinkRtcCall call,Channel channel) {
        log.info("hang up:" + new Gson().toJson(call));
    }
}
