package com.link.common.core.mq;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */
@Data
@Accessors(chain = true)
public class FanoutPushCommand {


    private Collection<String> userId;

    private short eventType;

    private String payloadType;

    private String payloadJson;

}
