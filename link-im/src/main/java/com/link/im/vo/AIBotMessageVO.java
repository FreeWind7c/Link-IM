package com.link.im.vo;

import com.link.im.dto.ai.AIBotMessageDTO;
import com.link.im.entity.base.BaseBootMessage;
import com.link.im.entity.base.BaseBotData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月12日
 */
@Data
@Accessors(chain = true)
public class AIBotMessageVO extends BaseBootMessage {

    public static AIBotMessageVO toVo(AIBotMessageDTO dto) {
        AIBotMessageVO vo = new AIBotMessageVO();;
        BeanUtils.copyProperties(dto,vo);

        return null;
    }
}
