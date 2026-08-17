package com.link.im.vo;

import com.link.im.entity.base.BaseBotData;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月12日
 */
public class AIBotMessageVO {

    private String id;

    private int seq;

    // 1文本 2媒体
    private int type;

    @Field("chat_id")
    private String chatId;

    @Field("snd_id")
    private String sndId;

    @Field("rcv_id")
    private String rcvId;

    private BaseBotData data;

    private long timestamp;

}
