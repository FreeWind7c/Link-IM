package com.link.im.entity.base;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月19日
 */
@Data
@Accessors(chain = true)
public class BaseBootMessage {

    private String id;

    private int seq;

    // 1用户 2AI
    private int senderType;

    private String chatId;

    private String sndId;

    private String rcvId;

    private BaseBotData data;

    private long timestamp;

}
