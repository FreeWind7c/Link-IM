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
        List<Channel> rcvChannels = this.config.getSessionManager().getChannel(call.getRcvId());
        List<Channel> sndChannels = this.config.getSessionManager().getChannel(call.getSndId());
        DefaultMessageInfo defaultMessageInfo = this.getMongoTemplate().findById(eq(where(col(DefaultMessageInfo::getId)).is(new ObjectId(call.getMessageId()))), DefaultMessageInfo.class);
        if (defaultMessageInfo == null)
            return;

        CallData data = (CallData) defaultMessageInfo.getBaseData();
        data.setStatus(2).setEndTime(now());
        Query eq = eq(
                where(col(DefaultMessageInfo::getId)).is(call.getMessageId())
        );
        Update update = update()
                .set("data", data.toJson());
        FindAndModifyOptions options = options();
        options.returnNew(true);
        options.upsert(false);
        DefaultMessageInfo messageInfo = this.findAndModify(eq, update, options);

        if (messageInfo == null)
            return;
        if (rcvChannels != null && !rcvChannels.isEmpty())
        {
            this.config.getLinkSender().send(EventType.RTC_CALL,rcvChannels,call);
            this.config.getLinkSender().send(EventType.DEFAULT_MESSAGE,rcvChannels,messageInfo);
        }

        this.config.getLinkSender().send(EventType.DEFAULT_MESSAGE,sndChannels,messageInfo);
    }
}
