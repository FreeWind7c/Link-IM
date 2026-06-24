package com.link.core.event.handler;

import com.link.common.serialize.service.LinkSerializer;
import com.link.core.sender.LinkMessageSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月17日
 */
@Component
public abstract class AbstractEventHandler {

//    @Qualifier("")
    @Autowired
    public LinkMessageSender linkSender;

}
