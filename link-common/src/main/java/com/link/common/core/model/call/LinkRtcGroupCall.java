package com.link.common.core.model.call;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 群通话（TRTC）状态同步帧，对应前端 LinkRtcGroupCall。
 * 由 PackData(action={@code RTC_GROUP_CALL=1020}) 承载。
 *
 * <p><b>这是「通知」不是「请求」</b>：事件已经在 TRTC 侧发生了，服务端否决不了，
 * 只能落库记账。这与一对一的 {@link LinkRtcCall} 有本质区别——那里服务端是信令中枢，
 * 转发行为本身构成事实；这里服务端只是旁观者。
 *
 * <p><b>字段不可信</b>：所有字段由上报方客户端填写。{@code sndId} 必须用连接上的
 * 登录态校验，否则用户可以伪造他人的通话状态。
 *
 * <p><b>可能重复、乱序、缺失</b>：
 * <ul>
 *   <li>重复：USER_ENTER/USER_LEAVE/REJECT 这类事件群内每个人都会收到并上报，
 *       同一事实会有 N 份副本，必须按 (messageId, eventType, targetId) 幂等；</li>
 *   <li>缺失：上报方关标签页 / 崩溃 / 断网都会丢事件，不能假设收得齐。
 *       长时间没等到 HANG_UP 的通话记录需要定时任务补偿，否则会永远停在「通话中」。</li>
 * </ul>
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Data
@Accessors(chain = true)
public class LinkRtcGroupCall {

    /** 上报方用户 ID。服务端必须用 Channel 上的登录态校验，不能直接信任 */
    private String sndId;

    private String groupId;

    /**
     * 事件所描述的目标用户 ID。
     * <ul>
     *   <li>自身状态类（CALL/ACCEPT/REJECT/HANG_UP）：等于 sndId；</li>
     *   <li>观察他人类（USER_ENTER/USER_LEAVE/NO_RESPONSE/LINE_BUSY/INVITE）：被观察者的 ID。
     *       这类事件多人会上报同一份，靠它做幂等键。</li>
     * </ul>
     */
    private String targetId;

    /** 群会话 ID，通话记录归属的会话 */
    private String chatId;

    /**
     * 本次通话的消息 ID，<b>贯穿整通通话的唯一标识</b>。
     * 由主叫生成，经 TRTC 的 userData 透传给所有被叫，使三端上报落到同一条记录。
     * 降级情况下（老客户端未透传）可能为空，此时靠 {@link #callId} 关联。
     */
    private String messageId;

    /** 群通话同步事件类型，见 {@code GroupRTCEventType} */
    private short eventType;

    /** 媒体类型：0=语音 1=视频，与一对一保持一致 */
    private int mediaType;

    /** TRTC 房间号，用于与腾讯云控制台对账排查，业务上非必需 */
    private String roomId;

    /** TRTC 侧通话唯一 ID。messageId 缺失时作为兜底关联键 */
    private String callId;

    /**
     * 完整参与者名单（含主叫自己）。
     * 仅 CALL 事件必填，服务端据此初始化参与者列表；其余事件为空。
     */
    private List<String> userIds;

    /** 通话时长（秒），仅 HANG_UP 有意义 */
    private long duration;

    /** 客户端事件时间戳（毫秒）。仅供参考，落库时间以服务端为准（客户端时钟不可信） */
    private long timestamp;
}
