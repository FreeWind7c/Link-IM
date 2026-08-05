package com.link.common.core.event;

import java.util.HashMap;
import java.util.Map;

/**
 * 群通话（TRTC）状态同步事件类型，对应前端 GroupRtcEventType。
 *
 * <p>与 {@link RTCEventType} 刻意分开：那一套是自建 WebRTC 的<b>协商信令</b>
 * （CALL/OFFER/ANSWER/CANDIDATE，后端是中枢，收到即转发给对端）；
 * 本枚举是 TRTC 群通话的<b>事后状态上报</b>——呼叫信令已在腾讯云 IM 通道上
 * 完成，后端看不见也管不着，只负责把既成事实记账。
 *
 * <p>两者语义完全不同，混用同一个枚举会让 handler 分不清「该转发」还是「该落库」。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
public enum GroupRTCEventType {

    /** 主叫发起群通话：建通话记录，只有主叫会发 */
    CALL((short) 1),
    /** 被叫收到来电（振铃），仅用于统计，可忽略 */
    RINGING((short) 2),
    /** 某人点了接听（尚未进房） */
    ACCEPT((short) 3),
    /** 某人拒接 */
    REJECT((short) 4),
    /** 某人进入房间：判定「真正加入通话」的可靠信号 */
    USER_ENTER((short) 5),
    /** 某人离开房间 */
    USER_LEAVE((short) 6),
    /** 某人超时未接听 */
    NO_RESPONSE((short) 7),
    /** 某人忙线 */
    LINE_BUSY((short) 8),
    /** 本端通话结束（接通后，带时长） */
    HANG_UP((short) 9),
    /** 通话未接通即结束：全员拒接 / 超时 / 主叫取消 */
    NOT_CONNECTED((short) 10),
    /** 通话中邀请了新成员 */
    INVITE((short) 11);

    private final short action;

    GroupRTCEventType(short action) {
        this.action = action;
    }

    private static final Map<Short, GroupRTCEventType> ACTION_INDEX = new HashMap<>();

    static {
        for (GroupRTCEventType type : values()) {
            ACTION_INDEX.put(type.action, type);
        }
    }

    /**
     * 根据协议中的 eventType 字段反查事件类型，未知 action 返回 null。
     */
    public static GroupRTCEventType fromAction(short action) {
        return ACTION_INDEX.get(action);
    }

    public short getAction() {
        return action;
    }
}
