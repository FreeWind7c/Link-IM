package com.link.base.vo;

import com.link.base.dto.ai.AIBotMessageDTO;
import com.link.base.entity.base.BaseBootMessage;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.BeanUtils;

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
