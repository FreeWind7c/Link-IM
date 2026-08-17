package com.link.im.handler.call;

import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.event.RTCEventType;
import com.link.common.core.model.call.LinkRtcCall;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.RTCEventHandler;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.data.CallData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.handler.LinkDefaultMessageEventHandler;
import com.link.im.mongo.BasePlatFormMongoService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
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
public class LinkRtcCallEventHandler extends BasePlatFormMongoService<DefaultMessageInfo> implements RTCEventHandler {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private MessageSeqAllocator seqAllocator;

    @Autowired
    private LinkDefaultMessageEventHandler handler;

    @Override
    public RTCEventType event() {
        return RTCEventType.CALL;
    }

    @Override
    public void handler(LinkRtcCall call,Channel channel) {
        List<Channel> channels = this.config.getSessionManager().getChannel(call.getRcvId());
        log.info("call: " + new Gson().toJson(call));
        MessageSeqAllocator.SeqResult allocate = this.seqAllocator.allocate(call.getChatId(), call.getMessageId());
        if (allocate.duplicate())
            return ;

        // 入库
        DefaultMessageInfo message = (DefaultMessageInfo) new DefaultMessageInfo().setId(new ObjectId(call.getMessageId()))
                .setType(MessageType.RTC_CALL_MESSAGE.getType())
                .setSndId(new ObjectId(call.getSndId()))
                .setRcvId(new ObjectId(call.getRcvId()))
                .setChatId(call.getChatId())
                .setState(1)
                .setData(new CallData().setStatus(0).setStartTime(now()).setMediaType(call.getMediaType()).setEndTime(0).toJson())
                .setTimestamp(now());
        message.setSeq((int) allocate.seq());
        this.insert(message);
        // 更新会话
        Query eq = eq(
                where(col(ChatSession::getChatId)).is(call.getChatId())
        );
        Update update = update()
                .set(col(ChatSession::getLastMsgSummary), MessageType.summaryOf(message.getType(), message.getBaseData()))
                .set(col(ChatSession::getLastMsgType), message.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), message.getSeq());
        this.getMongoTemplate().updateFirst(eq,update,ChatSession.class);

        if (channels != null)
            this.config.getLinkSender().send(EventType.RTC_CALL,channels,call);
    }
}
