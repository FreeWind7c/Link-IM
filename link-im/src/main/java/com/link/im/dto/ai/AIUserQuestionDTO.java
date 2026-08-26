package com.link.im.dto.ai;

import com.link.im.entity.base.BaseBotData;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月11日
 */
@Data
@Accessors(chain = true)
public class AIUserQuestionDTO {

    private String userId;

    private String userName;


    private BaseBotData data;


}
