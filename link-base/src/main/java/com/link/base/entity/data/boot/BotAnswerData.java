package com.link.base.entity.data.boot;

import com.link.base.entity.base.BaseBotData;
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
public class BotAnswerData extends BaseBotData {

    private String answer;

}
