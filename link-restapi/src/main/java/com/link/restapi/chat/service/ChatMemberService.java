package com.link.restapi.chat.service;


import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.ApiResult;
import com.link.restapi.chat.model.dto.LinkReportSessionDto;
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

        Update update = update().set(col(ChatSessionMember::getLastReadSeq), dto.getLastReadSeq());

FindAndModifyOptions options = new FindAndModifyOptions();
        options.upsert(true);
        options.returnNew(true);

        ChatSessionMember member = this.findAndModify(eq, update, options);

        return ApiResult.success();
    }
}
