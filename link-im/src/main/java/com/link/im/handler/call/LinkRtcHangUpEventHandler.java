package com.link.im.handler.call;

import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.RTCEventHandler;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import io.netty.channel.Channel;
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

        Query eq = eq(
                where(col(DefaultMessageInfo::getId)).is(call.getMessageId())
        );
        Update update = update()
                .set("data.status", 2)
                .set("data.end_time",now());
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
