package com.link.common.core.mq;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */
@Data
@Accessors(chain = true)
public class FanoutPushCommand {


    /** 目标用户 id（推给谁） */
    private List<String> userId;

    /** 事件类型，取 EventType.getAction()（如 ADD_FRIEND=1012），消费端用 EventType.fromAction 还原 */
    private short eventType;

    /** payload 的全限定类名，消费端据此把 payloadJson 反序列化回具体类型 */
    private String payloadType;

    /** payload 序列化后的 JSON，承载真正要推给端上的业务数据 */
    private String payloadJson;

}
