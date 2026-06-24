package com.link.restapi.chat.service;

import com.link.im.common.mongo.BaseMongoService;
import com.link.im.entity.chat.ChatMember;
import com.link.im.util.R;
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
public class ChatMemberService extends BaseMongoService<ChatMember> {



    public R reportSession(LinkReportSessionDto dto) {

        Query eq = eq(
                where(col(ChatMember::getOwnerId)).is(new ObjectId(dto.getUserId()))
                        .and(col(ChatMember::getChatId)).is(dto.getChatId())
        );

        Update update = update().set(col(ChatMember::getLastReadSeq), dto.getLastReadSeq());

FindAndModifyOptions options = new FindAndModifyOptions();
        options.upsert(true);
        options.returnNew(true);

        ChatMember member = this.findAndModify(eq, update, options);

        return R.ok();
    }
}
