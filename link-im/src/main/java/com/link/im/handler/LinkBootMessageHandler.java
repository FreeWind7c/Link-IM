package com.link.im.handler;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.link.common.core.event.EventType;
import com.link.common.core.model.ack.LinkAck;
import com.link.common.redis.RedisKeys;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.EventHandler;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.dto.ai.AIBotMessageDTO;
import com.link.im.dto.ai.AIUserQuestion;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.data.boot.BotAnswerData;
import com.link.im.entity.data.boot.BotQuestionData;
import com.link.im.entity.message.AIBotMessage;
import com.link.im.entity.message.type.MessageType;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.util.HttpClientUtil;
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
       try{
           AIBotMessageDTO dto = (AIBotMessageDTO) obj;
           print("dto: " , dto);
           MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(dto.getChatId(), dto.getId());

           if (seqResult.duplicate())
           {
               this.config.getLinkSender().send(EventType.ACK,channel,dto.getSeq());
               return;
           }
           dto.setSeq((int) seqResult.seq());

           BotQuestionData questionData = (BotQuestionData) dto.getData();
           print("question:" , new Gson().toJson(questionData));
           this.insert(new AIBotMessage().converterMessage(dto));
           redisTemplate.opsForList().rightPush(RedisKeys.BOOT_MESSAGE + dto.getSndId(),dto);
           redisTemplate.opsForList().trim(RedisKeys.BOOT_MESSAGE + dto.getSndId(),-100,-1);
           updateSession(dto,questionData.getQuestion());

           this.config.getLinkSender().send(EventType.ACK,channel,new LinkAck(dto.getId(),dto.getChatId(),dto.getSeq()));

           // TODO 调用AI大模型
           AIUserQuestion question = new AIUserQuestion();
           question.setData(dto.getData());
           question.setUserId(dto.getSndId());
           question.setUserName("小明");

           invokeBot(channel, question, dto);

       }catch (Exception e){
           e.printStackTrace();
       }
    }

    private void invokeBot(Channel channel, AIUserQuestion question, AIBotMessageDTO dto) {
        String body = HttpClientUtil.postJson(new Gson().toJson(question));
        JSONObject json = JSONObject.parseObject(body);
        BotAnswerData data = new BotAnswerData().setAnswer(json.getString("answer"));
        AIBotMessageDTO answer = new AIBotMessageDTO()
                .setId(new ObjectId().toHexString())
                .setSndId(dto.getRcvId())
                .setSenderType(2)
                .setRcvId(dto.getSndId())
                .setChatId(dto.getChatId())
                .setData(data)
                .setTimestamp(now());

        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(dto.getChatId(), dto.getId());
        answer.setSeq((int) seqResult.seq());

        this.insert(new AIBotMessage().converterMessage(answer));
        redisTemplate.opsForList().rightPush(RedisKeys.BOOT_MESSAGE + dto.getSndId(),dto);
        redisTemplate.opsForList().trim(RedisKeys.BOOT_MESSAGE + dto.getSndId(),-100,-1);

        updateSession(answer, data.getAnswer());

        this.config.getLinkSender().send(EventType.BOT_MESSAGE, channel,answer);
        print("AI回答：" , answer);
    }

    private void updateSession(AIBotMessageDTO dto, String summary) {

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
