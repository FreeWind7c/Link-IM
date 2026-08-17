package com.link.im.handler.call;

import com.google.gson.Gson;
import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.common.redis.RedisKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.RTCEventHandler;
import com.link.core.session.service.LinkSession;
import com.link.im.constants.redpack.RedPacketStatusKeys;
import com.link.im.entity.data.CallData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
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
        print("cancel:",call);
        List<Channel> rcvChannels = this.config.getSessionManager().getChannel(call.getRcvId());
        List<Channel> sndChannels = this.config.getSessionManager().getChannel(call.getSndId());
        DefaultMessageInfo defaultMessageInfo = this.findById(new ObjectId(call.getMessageId()));
        if (defaultMessageInfo == null)
            return;


        CallData data = (CallData) defaultMessageInfo.getBaseData();
        data.setStatus(defaultMessageInfo.getSndId().equals(call.getSndId()) ? 4 : 3);
        Query eq = eq(
                where(col(DefaultMessageInfo::getId)).is(call.getMessageId())
        );
        Update update = update().set("data", data.toJson());
        FindAndModifyOptions options = options();
        options.returnNew(true);
        options.upsert(false);
        DefaultMessageInfo messageInfo = this.getMongoTemplate().findAndModify(eq, update, options,DefaultMessageInfo.class);

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
