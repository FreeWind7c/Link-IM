package com.link.im.entity.data;

import com.google.gson.Gson;
import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

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
public class CallData extends BaseData {

    // 0语音通话 1视频通话
    @Field("media_type")
    private int mediaType;

    /**
     * 通话状态。
     * <p>一对一：0未接通 1已接通 2挂断 3拒绝 4取消 5未接听。
     * <p>群通话：0呼叫中 1通话中 2已结束 3无人接听（见下方常量）。
     */
    private int status;

    @Field("start_time")
    private long startTime;

    @Field("end_time")
    private long endTime;

    // ---------- 群通话扩展字段（一对一记录中为默认值） ----------

    /** 是否群通话。true 时 participants 有效，前端据此渲染多人气泡 */
    @Field("group_call")
    private boolean groupCall;

    /** TRTC 房间号，排查问题时与腾讯云控制台对账用 */
    @Field("room_id")
    private String roomId;

    /** TRTC 通话唯一 ID，messageId 缺失时的兜底关联键（建索引） */
    @Field("call_id")
    private String callId;

    /** 参与者及其状态。群通话专用 */
    private List<CallParticipant> participants;

    // ---------- 群通话状态常量 ----------

    /** 呼叫中，尚无人接听 */
    public static final int GROUP_CALLING = 0;
    /** 通话中（已有人接听并进房） */
    public static final int GROUP_CONNECTED = 1;
    /** 已结束（有人接听过，带时长） */
    public static final int GROUP_FINISHED = 2;
    /** 无人接听即结束（全员拒接 / 超时 / 主叫取消） */
    public static final int GROUP_NOT_CONNECTED = 3;

    public String toJson() {
        String json = new Gson().toJson(this,CallData.class);
        return json;
    }
}
