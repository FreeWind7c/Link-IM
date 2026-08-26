package com.link.im.handler.call;

import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.RTCEventHandler;
import com.link.im.entity.data.CallData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */

@Slf4j
@Component
public class LinkRtcHangUpEventHandler extends BasePlatFormMongoService<DefaultMessageInfo> implements RTCEventHandler {
    @Autowired
    private LinkCoreConfig config;

    @Override
    public RTCEventType event() {
        return RTCEventType.HANG_UP;
    }

    @Override
    public void handler(LinkRtcCall call,Channel channel) {
        log.info("hang up:" + new Gson().toJson(call));
    }
}
