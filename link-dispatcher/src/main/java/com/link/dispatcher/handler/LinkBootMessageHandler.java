package com.link.dispatcher.handler;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.model.ack.LinkAck;
import com.link.common.redis.RedisKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.EventHandler;
import com.link.base.seq.MessageSeqAllocator;
import com.link.base.dto.ai.AIBotMessageDTO;
import com.link.base.dto.ai.AIUserQuestionDTO;
import com.link.base.entity.base.BaseBootMessage;
import com.link.base.entity.chat.ChatSession;
import com.link.base.entity.data.boot.BotAnswerData;
import com.link.base.entity.data.boot.BotQuestionData;
import com.link.base.entity.message.AIBotMessage;
import com.link.base.entity.message.type.MessageType;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.dispatcher.util.HttpClientUtil;
import com.link.base.vo.AIBotMessageVO;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月11日
 */
@Slf4j
@Component
public class LinkBootMessageHandler extends BasePlatFormMongoService<AIBotMessage> implements EventHandler {


    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;



    @Override
    public EventType event() {
        return EventType.BOT_MESSAGE;
    }

    @Override
    public Class<?> bodyClass() {
        return AIBotMessageDTO.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        AIBotMessageDTO dto = (AIBotMessageDTO) obj;
        printf("boot data:" , dto);
        try {
            if (handleDuplicateMessage(dto, channel)) {
                return;
            }

            allocateSeqAndPersistQuestion(dto);
            sendAck(channel, dto);
            invokeBot(channel, buildUserQuestion(dto), dto);

        } catch (Exception e) {
            log.error("Bot message handler error, chatId: {}, userId: {}", dto.getChatId(), dto.getSndId(), e);
            handleBotError(channel, dto);
        }
    }

    private boolean handleDuplicateMessage(AIBotMessageDTO dto, Channel channel) {
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(dto.getChatId(), dto.getId());
        if (seqResult.duplicate()) {
            this.config.getLinkSender().send(EventType.ACK, channel, dto.getSeq());
            return true;
        }
        dto.setSeq((int) seqResult.seq());
        return false;
    }

    private void allocateSeqAndPersistQuestion(AIBotMessageDTO dto) {
        BotQuestionData questionData = (BotQuestionData) dto.getData();
        persistMessage(dto, AIBotMessage.converterMessage(dto, 1));
        updateSession(dto, questionData.getQuestion());
    }

    private AIUserQuestionDTO buildUserQuestion(AIBotMessageDTO dto) {
        AIUserQuestionDTO question = new AIUserQuestionDTO();
        question.setData(dto.getData());
        question.setUserId(dto.getSndId());
        question.setUserName(dto.getSndId());
        return question;
    }

    private void sendAck(Channel channel, AIBotMessageDTO dto) {
        this.config.getLinkSender().send(EventType.ACK, channel, new LinkAck(dto.getId(), dto.getChatId(), dto.getSeq()));
    }

    private void handleBotError(Channel channel, AIBotMessageDTO dto) {
        BotAnswerData errorData = new BotAnswerData().setAnswer("请求频繁，请稍后重试!");
        AIBotMessageVO errorAnswer = buildBotAnswer(dto, errorData);

        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(dto.getChatId(), errorAnswer.getId());
        errorAnswer.setSeq((int) seqResult.seq());

        persistAnswer(errorAnswer);
        updateSession(errorAnswer, errorData.getAnswer());

        this.config.getLinkSender().send(EventType.BOT_MESSAGE, channel, errorAnswer);
    }

    private void invokeBot(Channel channel, AIUserQuestionDTO question, AIBotMessageDTO dto) {
        String body = HttpClientUtil.postJson(new Gson().toJson(question));
        JSONObject json = JSONObject.parseObject(body);
        BotAnswerData data = new BotAnswerData().setAnswer(json.getString("answer"));

        AIBotMessageVO answer = buildBotAnswer(dto, data);
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(dto.getChatId(), answer.getId());
        answer.setSeq((int) seqResult.seq());

        persistAnswer(answer);
        updateSession(answer, data.getAnswer());

        this.config.getLinkSender().send(EventType.BOT_MESSAGE, channel, answer);
        printf("AI回答：", answer);
    }

    private void persistMessage(AIBotMessageDTO dto, AIBotMessage message) {
        this.insert(message);
        redisTemplate.opsForList().rightPush(RedisKeys.BOOT_MESSAGE + dto.getSndId(), dto);
        redisTemplate.opsForList().trim(RedisKeys.BOOT_MESSAGE + dto.getSndId(), -100, -1);
    }

    private void persistAnswer(AIBotMessageVO answer) {
        this.insert(AIBotMessage.converterMessage(answer, 2));
        redisTemplate.opsForList().rightPush(RedisKeys.BOOT_MESSAGE + answer.getRcvId(), answer);
        redisTemplate.opsForList().trim(RedisKeys.BOOT_MESSAGE + answer.getRcvId(), -100, -1);
    }

    private AIBotMessageVO buildBotAnswer(AIBotMessageDTO dto, BotAnswerData data) {
        return (AIBotMessageVO) new AIBotMessageVO()
                .setId(new ObjectId().toHexString())
                .setSndId(dto.getRcvId())
                .setSenderType(2)
                .setRcvId(dto.getSndId())
                .setChatId(dto.getChatId())
                .setData(data)
                .setTimestamp(now());
    }

    private void updateSession(BaseBootMessage dto, String summary) {

        Query eq = eq(
                where(col(ChatSession::getChatId)).is(dto.getChatId())
                        .and(col(ChatSession::getLastMsgSeq)).lt(dto.getSeq())
        );

        Update update = update()
                .set(col(ChatSession::getLastMsgSummary), summary)
                .set(col(ChatSession::getLastMsgType), MessageType.TEXT_MESSAGE.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), dto.getSeq());

        this.getMongoTemplate().updateFirst(eq, update,ChatSession.class);
    }

}
