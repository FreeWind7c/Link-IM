package com.link.im.entity.data.boot;

import com.link.im.entity.base.BaseBotData;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月13日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class BotQuestionData extends BaseBotData {

    private String question;

    private BotMediaData media;

}
