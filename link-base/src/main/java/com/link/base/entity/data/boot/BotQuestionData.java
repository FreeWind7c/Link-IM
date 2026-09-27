package com.link.base.entity.data.boot;

import com.link.base.entity.base.BaseBotData;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月13日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class BotQuestionData extends BaseBotData {

    private String question;

    private List<BotAttachmentData> data;

}
