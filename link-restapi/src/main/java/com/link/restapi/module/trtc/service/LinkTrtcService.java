package com.link.restapi.module.trtc.service;

import com.alibaba.fastjson.JSONObject;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.constants.trtc.TrtcCallStatusCode;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.data.NoticeData;
import com.link.im.entity.data.notice.TrtcCallNoticeData;
import com.link.im.entity.message.type.MessageType;
import com.link.im.entity.rtc.TrtcCallInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.ApiResult;
import com.link.restapi.module.trtc.model.dto.LinkTrtcRoomIdDTO;
import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class LinkTrtcService extends BasePlatFormMongoService<TrtcCallInfo> {

    @Autowired
    private MessageSeqAllocator allocator;

    @Autowired
    private RemotePushPublisher pushPublisher;

    public void dissolveRoom(JSONObject eventInfo) {
        Query query = eq(
                where(col(TrtcCallInfo::getRoomId)).is(eventInfo.getString("RoomId"))
        );
        Update update = update()
                .set(col(TrtcCallInfo::getStatus), TrtcCallStatusCode.CALL_ENDED)
                .set(col(TrtcCallInfo::getEndTime),now());
        TrtcCallInfo call = this.findOne(query);
        this.updateFirst(query,update);

        ObjectId messageId = new ObjectId();
        TrtcCallNoticeData trtcCallNoticeData = new TrtcCallNoticeData().setText("群通话结束");
        NoticeData noticeData = new NoticeData().setChatId(call.getChatId()).setData(trtcCallNoticeData);
        MessageSeqAllocator.SeqResult result = allocator.allocate(call.getChatId(), messageId.toHexString());
        BaseMessage message = new BaseMessage().setId(messageId)
                .setSeq((int)result.seq())
                .setType(MessageType.NOTICE_MESSAGE.getType())
                .setChatId(call.getChatId())
                .setSndId(call.getInitiatorId())
                .setRcvId(call.getReceiverId())
                .setState(1)
                .setData(noticeData.toJson())
                .setTimestamp(now());
        this.pushPublisher.pushMessageStorage(message,call.isGroupCall() ? 2 : 1);
        log.info("解散房间:"+eventInfo.getString("RoomId"));
    }

    public void joinRoom(JSONObject eventInfo) {
        Query query = eq(
                where(col(TrtcCallInfo::getRoomId)).is(eventInfo.getString("RoomId"))
        );
        Update update = update().addToSet(col(TrtcCallInfo::getParticipantIds), eventInfo.getString("UserId"));
        this.updateFirst(query,update);
        log.info("加入房间:"+eventInfo.getString("UserId"));
    }

    public void leaveRoom(JSONObject eventInfo) {
        log.info("离开房间:"+eventInfo.getString("RoomId"));
    }

    public ApiResult startCall(LinkTrtcRoomIdDTO dto) {
        String roomId = new ObjectId().toHexString();
        TrtcCallInfo trtcCallInfo = createRtcCall(dto, roomId);
        TrtcCallNoticeData trtcCallNoticeData = new TrtcCallNoticeData().setText(dto.getSndName() + "发起了群通话");
        NoticeData data = new NoticeData().setChatId(dto.getChatId()).setData(trtcCallNoticeData);
        this.insert(trtcCallInfo);
        this.pushPublisher.pushMessageStorage(createMessage(dto, data),dto.getType());
        return ApiResult.success().setData(roomId);
    }

    private TrtcCallInfo createRtcCall(LinkTrtcRoomIdDTO dto, String roomId) {
        TrtcCallInfo trtcCallInfo = new TrtcCallInfo();
        trtcCallInfo.setRoomId(roomId)
                .setInitiatorId(new ObjectId(dto.getSndId()))
                .setReceiverId(new ObjectId(dto.getRcvId()))
                .setChatId(dto.getChatId())
                .setStartTime(System.currentTimeMillis())
                .setEndTime(0L)
                .setGroupCall(dto.getType() == 2)
                .setStatus(TrtcCallStatusCode.NO_ANSWER);
        return trtcCallInfo;
    }

    private BaseMessage createMessage(LinkTrtcRoomIdDTO dto, NoticeData data) {
        MessageSeqAllocator.SeqResult allocate = allocator.allocate(dto.getChatId(), dto.getMessageId());
        return new BaseMessage().setId(new ObjectId(dto.getMessageId()))
                .setChatId(dto.getChatId())
                .setSeq((int) allocate.seq())
                .setType(MessageType.NOTICE_MESSAGE.getType())
                .setSndId(new ObjectId(dto.getSndId()))
                .setRcvId(new ObjectId(dto.getRcvId()))
                .setState(1)
                .setData(data.toJson())
                .setBaseData(data)
                .setTimestamp(System.currentTimeMillis())
                .setQuote(null);
    }
}
