package com.link.common.core.event;

import java.util.HashMap;
import java.util.Map;

public enum  EventType {

    HEARTBEAT((short) 1),
    ACK((short) 2),

    LOGIN((short) 1001),

    LOGOUT((short) 1002),

    DEFAULT_MESSAGE((short) 1010),

    GROUP_MESSAGE((short) 1011),

    ADD_FRIEND((short) 1012),
    APPROVE_FRIEND((short) 1013),
    REMOVE_GROUP_MEMBER((short) 1014),
    JOIN_GROUP((short) 1015),
    NOTICE_MESSAGE((short) 1016),

    SINGLE_FORWARD((short) 1017),

    MERGE_FORWARD((short) 1018),
    RTC_CALL((short) 1019),

    RTC_GROUP_CALL((short) 1020),

    USER_EXIT((short) 1021),
    UPDATE_MESSAGE((short) 1022),
    BOT_MESSAGE((short) 1023);



    private short action;

    EventType(short action) {
        this.action = action;
    }

    private static final Map<Short, EventType> ACTION_INDEX = new HashMap<>();

    static {
        for (EventType type : values()) {
            ACTION_INDEX.put(type.action, type);
        }
    }

    /**
     * 根据协议中的 action 字段反查事件类型，未知 action 返回 null。
     */
    public static EventType fromAction(short action) {
        return ACTION_INDEX.get(action);
    }

    public short getAction() {
        return action;
    }

}