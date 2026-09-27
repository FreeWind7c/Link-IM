package com.link.base.repository;

import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.message.type.MessageType;
import com.link.base.mongo.BasePlatFormMongoService;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月22日
 */

@Slf4j
public class ChatSessionRepository extends BasePlatFormMongoService<ChatSession> {

    public UpdateResult updateSession(BaseMessage message){
        // 更新会话
        Query eq = eq(
                where(col(ChatSession::getChatId)).is(message.getChatId())
                        .and(col(ChatSession::getLastMsgSeq)).lt(message.getSeq())
        );
        Update update = update()
                .set(col(ChatSession::getLastMsgSummary),
                        MessageType.summaryOf(message.getType(), message.getBaseData()))
                .set(col(ChatSession::getLastMsgType), message.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), message.getSeq());

        return this.getMongoTemplate().updateFirst(eq, update, ChatSession.class);
    }

}
