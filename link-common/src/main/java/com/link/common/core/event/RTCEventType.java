package com.link.common.core.event;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
public enum RTCEventType {


    CALL((short) 1),
    ACCEPT((short) 2),
    CANCEL((short) 3),
    OFFER((short) 4),
    ANSWER((short) 5),
    CANDIDATE((short) 6),
    HANG_UP((short) 7),
    UNANSWERED((short) 8);

    private short action;

    RTCEventType(short action) {
        this.action = action;
    }

    private static final Map<Short, RTCEventType> ACTION_INDEX = new HashMap<>();

    static {
        for (RTCEventType type : values()) {
            ACTION_INDEX.put(type.action, type);
        }
    }

    /**
     * 根据协议中的 action 字段反查事件类型，未知 action 返回 null。
     */
    public static RTCEventType fromAction(short action) {
        return ACTION_INDEX.get(action);
    }

    public short getAction() {
        return action;
    }

}
