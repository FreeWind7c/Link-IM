package com.link.restapi.module.trtc.service;

import com.alibaba.fastjson.JSONObject;
import com.link.common.core.event.EventType;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.common.constants.trtc.TrtcCallStatusCode;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.data.CallData;
import com.link.im.entity.data.NoticeData;
import com.link.im.entity.data.notice.DefaultNoticeData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.entity.rtc.TrtcCallInfo;
import com.link.im.factory.LinkBaseDataFactory;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.service.LinkRedisService;
import com.link.restapi.utils.ApiResult;
import com.link.im.vo.DefaultMessageVO;
import com.link.im.vo.GroupMessageVO;
import com.link.im.vo.base.BaseMessageVO;
import com.link.restapi.module.trtc.model.dto.LinkTrtcCallDTO;
import com.link.restapi.module.trtc.model.dto.LinkTrtcRoomIdDTO;
import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;


@Slf4j
@Component
public class LinkTrtcService extends BasePlatFormMongoService<TrtcCallInfo> {

    @Autowired
    private MessageSeqAllocator allocator;

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Autowired
    private LinkRedisService redisService;

    @Autowired
    private LinkBaseDataFactory factory;

    public void dissolveRoom(JSONObject eventInfo) {
        Query query = eq(
                where(col(TrtcCallInfo::getRoomId)).is(eventInfo.getString("RoomId"))
        );
        Update update = update()
                .set(col(TrtcCallInfo::getStatus), TrtcCallStatusCode.CALL_ENDED)
                .set(col(TrtcCallInfo::getEndTime),now());
        TrtcCallInfo call = this.findOne(query);

        if (!call.isGroupCall())
            return;

        this.updateFirst(query,update);

        ObjectId messageId = new ObjectId();

        NoticeData noticeData = this.factory.createDefaultNoticeData(call.getChatId(),"群通话结束");
        MessageSeqAllocator.SeqResult result = allocator.allocate(call.getChatId(), messageId.toHexString());

        // 根据通话类型创建对应的消息子类实例
        BaseMessageVO message = call.isGroupCall()
                ? new GroupMessageVO()
                : new DefaultMessageVO();

        message.setId(messageId.toHexString())
                .setSeq((int)result.seq())
                .setType(MessageType.NOTICE_MESSAGE.getType())
                .setChatId(call.getChatId())
                .setSndId(call.getInitiatorId().toHexString())
                .setRcvId(call.getReceiverId().toHexString())
                .setState(1)
                .setData(noticeData.toJson())
                .setBaseData(noticeData)
                .setTimestamp(now());
        Set<String> memberIds = redisService.getChatMemberIds(call.getChatId());
        this.pushPublisher.messageStorage(message, call.isGroupCall() ? 2 : 1);
        this.pushPublisher.push(
                !call.isGroupCall() ? EventType.DEFAULT_MESSAGE : EventType.GROUP_MESSAGE,
                memberIds,
                message);
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

    @Transactional(rollbackFor = Exception.class)
    public ApiResult startCall(LinkTrtcRoomIdDTO dto) {
        String roomId = new ObjectId().toHexString();
        TrtcCallInfo trtcCallInfo = createRtcCall(dto, roomId);
        BaseData data = createBaseData(dto,roomId);
        this.insert(trtcCallInfo);

        Set<String> memberIds = redisService.getChatMemberIds(dto.getChatId());
        BaseMessageVO message = createMessage(dto, data);

        this.pushPublisher.messageStorage(message,dto.getType());
        if (dto.getType() == 2)
            this.pushPublisher.push(
                    dto.getType() == 1 ? EventType.DEFAULT_MESSAGE : EventType.GROUP_MESSAGE,
                    memberIds,
                    message
            );
        return ApiResult.success().setData(roomId);
    }

    private BaseData createBaseData(LinkTrtcRoomIdDTO dto, String roomId) {
        if (dto.getType() == 1) {
            return new CallData().setRoomId(roomId);
        }else{
            return this.factory.createDefaultNoticeData(dto.getChatId(),dto.getSndName() + "发起了群通话");
        }
    }

    private TrtcCallInfo createRtcCall(LinkTrtcRoomIdDTO dto, String roomId) {
        TrtcCallInfo trtcCallInfo = new TrtcCallInfo();
        trtcCallInfo.setMessageId(new ObjectId(dto.getMessageId()))
                .setRoomId(roomId)
                .setInitiatorId(new ObjectId(dto.getSndId()))
                .setReceiverId(new ObjectId(dto.getRcvId()))
                .setChatId(dto.getChatId())
                .setMediaType(dto.getMediaType())
                .setStartTime(System.currentTimeMillis())
                .setEndTime(0L)
                .setGroupCall(dto.getType() == 2)
                .setStatus(TrtcCallStatusCode.NO_ANSWER);
        return trtcCallInfo;
    }

    private BaseMessageVO createMessage(LinkTrtcRoomIdDTO dto, BaseData data) {

        MessageSeqAllocator.SeqResult allocate =
                allocator.allocate(dto.getChatId(), dto.getMessageId());

        BaseMessageVO messageInfo = dto.getType() == 1
                ? new DefaultMessageVO()
                : new GroupMessageVO();

            return  messageInfo
                    .setId(dto.getMessageId())
                    .setChatId(dto.getChatId())
                    .setSeq((int) allocate.seq())
                    .setType(dto.getType() == 1 ? MessageType.RTC_CALL_MESSAGE.getType() : MessageType.NOTICE_MESSAGE.getType())
                    .setSndId(dto.getSndId())
                    .setRcvId(dto.getRcvId())
                    .setState(1)
                    .setData(data.toJson())
                    .setBaseData(data)
                    .setTimestamp(System.currentTimeMillis())
                    .setQuote(null);

    }

    public ApiResult hangUpCall(LinkTrtcCallDTO dto) {
        Query eq = eq(
                where(col(TrtcCallInfo::getRoomId)).is(dto.getRoomId())
        );
        TrtcCallInfo call = this.findOne(eq);

        Update update = update()
                .set(col(TrtcCallInfo::getStatus), dto.getEventType())
                .set(col(TrtcCallInfo::getEndTime),now());
        FindAndModifyOptions options = options().upsert(false).returnNew(true);

        TrtcCallInfo result = this.findAndModify(eq, update, options);
        if (result == null)
            return ApiResult.error();

        DefaultMessageInfo message = this.getMongoTemplate().findById(call.getMessageId(), DefaultMessageInfo.class);
        DefaultMessageVO vo = message.toVo();
        CallData data = CallData.toData(result);
        vo.setData(data.toJson()).setBaseData(data);
        Set<String> memberIds = redisService.getChatMemberIds(call.getChatId());

        this.pushPublisher.push(EventType.DEFAULT_MESSAGE,memberIds,vo);
        return ApiResult.success();
    }

    public ApiResult acceptCall(LinkTrtcCallDTO dto) {
        Query eq = eq(
                where(col(TrtcCallInfo::getRoomId)).is(dto.getRoomId())
        );
        Update update = update().set(col(TrtcCallInfo::getStartTime), now());
        this.updateFirst(eq,update);
        return ApiResult.success();
    }
}
