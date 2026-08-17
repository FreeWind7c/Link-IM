package com.link.im.config;

import com.link.common.core.model.ack.LinkAck;
import com.link.common.serialize.LinkAckDeserializer;
import com.link.common.serialize.LinkJsonSerializer;
import com.link.common.serialize.ObjectIdTypeAdapter;
import com.link.core.config.LinkCoreConfig;
import com.link.im.entity.base.BaseBotData;
import com.link.im.entity.base.BaseMessage;
import com.link.im.serialize.BaseBotDataDeserializer;
import com.link.im.serialize.BaseMessageDeserializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * @Author: 无敌代码写手
 */
@Configuration
@RequiredArgsConstructor
public class LinkImSerializeConfig {

    private final LinkCoreConfig coreConfig;

    @PostConstruct
    public void registerMessageAdapter() {
        this.coreConfig.setLinkSerializer(new LinkJsonSerializer(
                Map.<Class<?>, Object>of(
                        // BaseMessage 有 DefaultMessageInfo / GroupMessageInfo 等子类，走 hierarchy
                        BaseMessage.class, new BaseMessageDeserializer(),
                        // BaseBotData 有 BotQuestionData / BotAnswerData 子类，同样走 hierarchy
                        BaseBotData.class, new BaseBotDataDeserializer()),
                Map.<Class<?>, Object>of(
                        // ObjectId 精确匹配，String ↔ ObjectId 转换
                        ObjectId.class, new ObjectIdTypeAdapter(),
                        // LinkAck 精确匹配，兼容前端发送的对象格式 id
                        LinkAck.class, new LinkAckDeserializer())
        ));
    }
}
