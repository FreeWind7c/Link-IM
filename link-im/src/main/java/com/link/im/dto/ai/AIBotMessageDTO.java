package com.link.im.dto.ai;

import com.link.im.entity.base.BaseBotData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月11日
 */
@Data
@Accessors(chain = true)
public class AIBotMessageDTO {


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
