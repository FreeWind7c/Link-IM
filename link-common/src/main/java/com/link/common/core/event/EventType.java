package com.link.common.core.event;

import java.util.HashMap;
import java.util.Map;

public enum EventType {

    HEARTBEAT((short) 1),
    ACK((short) 2),

    LOGIN((short) 1001),

    LOGOUT((short) 1002),

    DEFAULT_MESSAGE((short) 1010),

    GROUP_MESSAGE((short) 1011),

    ADD_FRIEND((short) 1012), APPROVE_FRIEND((short) 1013);



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