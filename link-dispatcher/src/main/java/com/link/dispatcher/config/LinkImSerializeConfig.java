package com.link.dispatcher.config;

import com.link.common.core.model.ack.LinkAck;
import com.link.common.serialize.LinkAckDeserializer;
import com.link.common.serialize.LinkJsonSerializer;
import com.link.common.serialize.ObjectIdTypeAdapter;
import com.link.core.config.LinkCoreConfig;
import com.link.base.entity.base.BaseBotData;
import com.link.base.entity.base.BaseMessage;
import com.link.dispatcher.serialize.BaseBotDataDeserializer;
import com.link.dispatcher.serialize.BaseMessageDeserializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Netty 服务器序列化配置
 * 只在 LinkCoreConfig 存在时才加载（即只在 Netty 进程中生效）
 *
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
