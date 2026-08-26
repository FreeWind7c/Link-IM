package com.link.restapi.module.chat.service;


import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.chat.model.dto.LinkReportSessionDto;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月24日

 */
@Slf4j
@Component
public class ChatMemberService extends BasePlatFormMongoService<ChatSessionMember> {



    public ApiResult reportSession(LinkReportSessionDto dto) {

        Query eq = eq(
                where(col(ChatSessionMember::getOwnerId)).is(new ObjectId(dto.getUserId()))
                        .and(col(ChatSessionMember::getChatId)).is(dto.getChatId())
        );

        Update update = update()
                .set(col(ChatSessionMember::getLastReadSeq), dto.getLastReadSeq())
                // 进 / 出会话即视为已看到 @提醒：清空未读@我列表，列表「[有人@我]」随之消失。
                // 前端在调本接口前须已缓存 atList，会话内浮动按钮跳转用缓存那份，不依赖服务端。
                .set(col(ChatSessionMember::getAtList), java.util.Collections.emptyList());

FindAndModifyOptions options = new FindAndModifyOptions();
        options.upsert(true);
        options.returnNew(true);

        ChatSessionMember member = this.findAndModify(eq, update, options);

        return ApiResult.success();
    }
}
