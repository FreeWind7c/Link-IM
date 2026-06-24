package com.link.im.config;

import com.link.common.serialize.LinkJsonSerializer;
import com.link.core.config.LinkCoreConfig;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.serialize.AbstractMessageDeserializer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * 业务序列化配置。link-core 引擎默认使用裸 JSON 序列化器（不认识任何业务类型），
 * 这里在启动时把带 {@link AbstractMessage} 多态适配器的序列化器注入回 {@link LinkCoreConfig}，
 * 覆盖其默认实例。
 *
 * <p>时机说明：{@code @PostConstruct} 在 Spring 容器刷新阶段执行，早于
 * {@code DefaultServer}（ApplicationRunner）绑定端口、开始派发消息，因此覆盖发生在
 * 任何反序列化之前，安全。这是“业务知识留在 link-im、引擎保持中立”的衔接点。
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
                Collections.<Class<?>, Object>singletonMap(
                        AbstractMessage.class, new AbstractMessageDeserializer())));
    }
}
