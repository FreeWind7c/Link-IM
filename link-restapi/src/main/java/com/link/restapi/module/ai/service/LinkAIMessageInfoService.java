package com.link.restapi.module.ai.service;

import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.module.ai.model.dto.LinkSearchMessageDTO;
import com.link.restapi.module.message.model.dto.LinkAIPullMessageDTO;
import com.link.restapi.module.message.service.LinkMessageInfoService;
import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.swing.plaf.ActionMapUIResource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */
@Slf4j
@Component
public class LinkAIMessageInfoService extends BasePlatFormMongoService<DefaultMessageInfo> {

    @Autowired
    private LinkMessageInfoService messageInfoService;

    public ApiResult pullMessage(LinkAIPullMessageDTO dto) {
        if (!stringValidator(dto.getUserId())
                || (dto.getSessionType() != 1 && dto.getSessionType() != 2)
                || dto.getLimit() <= 0
                || dto.getStartTime() <= 0
                || dto.getEndTime() <=0)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        List<String> chatId = this.getMongoTemplate().find(
                eq(where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(dto.getUserId()))),
                ChatSessionMember.class
        ).stream().map(v -> { return v.getChatId();
        }).collect(Collectors.toList());

        Criteria criteria = where(col(BaseMessage::getChatId))
                .in(chatId)
                .and(col(BaseMessage::getTimestamp))
                .gte(dto.getStartTime())
                .lte(dto.getEndTime());
        Query query = eq(criteria);
        query.with(Sort.by(Sort.Direction.DESC,col(BaseMessage::getTimestamp)));
        query.limit(dto.getLimit());


        return ApiResult.success().setData(dto.getSessionType() == 1
                ? messageInfoService.createVo(this.getMongoTemplate().find(query,DefaultMessageInfo.class))
                : messageInfoService.createGroupVo(this.getMongoTemplate().find(query,GroupMessageInfo.class)));
    }

    public ApiResult searchMessage(LinkSearchMessageDTO dto) {
        if (!stringValidator(dto.getUserId(),dto.getChatId()) || dto.getLimit() < 1)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        ChatSession session = this.getMongoTemplate().findOne(eq(where(col(ChatSession::getChatId)).is(dto.getChatId())), ChatSession.class);

        Query query = eq(where(col(BaseMessage::getChatId)).is(dto.getChatId()));
        query.with(Sort.by(Sort.Direction.DESC,col(BaseMessage::getTimestamp)));
        query.limit(dto.getLimit());

        return ApiResult.success().setData(
                session.getType() == 1
                        ? messageInfoService.createVo(this.find(query))
                        : messageInfoService.createGroupVo(this.getMongoTemplate().find(query,GroupMessageInfo.class))
        );
    }

}
