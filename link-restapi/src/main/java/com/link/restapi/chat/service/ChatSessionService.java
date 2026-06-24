package com.link.restapi.chat.service;

import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.mongo.BaseMongoService;
import com.link.im.entity.chat.ChatMember;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.user.UserInfo;
import com.link.im.util.R;
import com.link.restapi.chat.model.dto.LinkCreateChatDto;
import com.link.restapi.chat.model.dto.LinkPullChatDTO;
import com.link.restapi.chat.model.vo.LinkChatSessionVo;
import com.link.util.id.ChatIdGenerator;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月22日
 */
@Component
public class ChatSessionService extends BaseMongoService<ChatSession> {
    public R pullChat(LinkPullChatDTO dto) {
        Query eq = eq( where(col(ChatMember::getOwnerId))
                .is(new ObjectId(dto.getUserId()))
        );
        eq.skip(dto.getSkip());
        eq.limit(dto.getLimit());
        List<ChatMember> chatMembers = this.getMongoTemplate().find(eq, ChatMember.class);

        Map<String, ChatMember> sessionMap = chatMembers.stream().collect(Collectors.toMap(ChatMember::getChatId,
                Function.identity()));
        Map<ObjectId, ChatMember> memberMap = chatMembers.stream().collect(Collectors.toMap(ChatMember::getTargetId,
                Function.identity()));
        List<ChatSession> chatSessions = this.find(eq( where(col(ChatSession::getChatId))
                .in(sessionMap.keySet())));
        Map<String, ChatSession> map = chatSessions.stream().collect(Collectors.toMap(ChatSession::getChatId,
                Function.identity()));
        List<UserInfo> userInfos = this.getMongoTemplate().find(eq( where(col(UserInfo::getId)).in(memberMap.keySet())),
                UserInfo.class);

        return R.ok().setData(createChatInfoVos(userInfos, memberMap, map));
    }

    private static List<LinkChatSessionVo> createChatInfoVos(List<UserInfo> userInfos, Map<ObjectId, ChatMember> memberMap, Map<String, ChatSession> map) {
        List<LinkChatSessionVo> vos = userInfos.stream().map(item -> {
            ChatMember member = memberMap.get(item.getId());
            ChatSession session = map.get(member.getChatId());
            return new LinkChatSessionVo().createVo(session,member,item);
        }).collect(Collectors.toList());
        return vos;
    }

    public R createChat(LinkCreateChatDto dto) {
        if (StringUtils.isEmpty(dto.getUserId()) || StringUtils.isEmpty(dto.getTargetId()))
            return R.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String chatId = ChatIdGenerator.nextId(dto.getUserId(), dto.getTargetId());
        ChatSession session = new ChatSession().createSingle(chatId);
        ChatMember member = new ChatMember().createSingle(dto.getUserId(), dto.getTargetId(), chatId);
        Query eq = eq(
                where(col(ChatSession::getChatId)).is(chatId)
        );
        Update update = update()
                .setOnInsert(col(ChatSession::getChatId), session.getChatId())
                .setOnInsert(col(ChatSession::getType), session.getType())
                .setOnInsert(col(ChatSession::getLastMsgSeq), session.getLastMsgSeq())
                .setOnInsert(col(ChatSession::getLastMsgSummary), session.getLastMsgSummary())
                .setOnInsert(col(ChatSession::getLastMsgTime), session.getLastMsgTime());
        FindAndModifyOptions options = FindAndModifyOptions.options()
                .upsert(true)
                .returnNew(true);
        this.findAndModify(eq,update,options);
        this.getMongoTemplate().insert(member);
        return R.ok();
    }


}
