package com.link.base.entity.data;

import com.google.gson.Gson;
import com.link.base.entity.base.BaseData;
import com.link.base.entity.message.type.MessageType;
import com.link.base.entity.rtc.TrtcCallInfo;

import com.link.base.provider.MessageTypeProvider;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 通话记录消息（RTC_CALL_MESSAGE=1007）的 data 体，一对一与群通话共用。
 *
 * <p>共用而非新建消息类型，是为了让前端的通话气泡渲染逻辑保持一份：
 * 群通话多出来的字段（groupCall/participants/roomId）对一对一记录为空，
 * 老数据天然兼容。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class CallData extends BaseData implements MessageTypeProvider {

    private String messageId;

    private String chatId;

    private String roomId;

    private int status;

    // 1语音 2视频
    private int mediaType;

    private long startTime;

    private long endTime;


    public static CallData toData(TrtcCallInfo call) {
        return new CallData().setMessageId(call.getMessageId().toHexString())
                .setChatId(call.getChatId())
                .setRoomId(call.getRoomId())
                .setStartTime(call.getStartTime())
                .setEndTime(call.getEndTime())
                .setStatus(call.getStatus())
                .setMediaType(call.getMediaType());
    }

    public String toJson() {
        String json = new Gson().toJson(this,CallData.class);
        return json;
    }

    @Override
    public int getMessageType() {
        return MessageType.RTC_CALL_MESSAGE.getType();
    }
}
