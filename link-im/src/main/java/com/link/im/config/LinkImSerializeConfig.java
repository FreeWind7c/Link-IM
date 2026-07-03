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
