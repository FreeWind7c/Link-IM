package com.link.restapi.module.ai.service;

import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.user.UserInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.module.ai.model.dto.LinkQueryChatSessionDTO;
import com.link.restapi.module.chat.model.vo.LinkChatSessionVo;
import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@Slf4j
@Component
public class LinkAIChatSessionService extends BasePlatFormMongoService<ChatSession> {
    public ApiResult queryChat(LinkQueryChatSessionDTO dto) {
        if (!stringValidator(dto.getChatId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);


        Query query = eq(where(col(ChatSession::getChatId)).is(dto.getChatId()));
        ChatSession session = this.findOne(query);
        if (session.getType() == 1)
        {
            this.findOne(eq(where(col(UserInfo::getId))));
        }

        return ApiResult.success().setData(session);
    }
}
