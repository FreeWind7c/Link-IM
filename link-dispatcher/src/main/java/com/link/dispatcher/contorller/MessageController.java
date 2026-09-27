package com.link.dispatcher.contorller;

import com.google.gson.Gson;
import com.link.base.dto.message.DefaultMessageDTO;
import com.link.base.entity.data.TextData;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.seq.MessageSeqAllocator;
import com.link.core.config.LinkCoreConfig;
import com.link.dispatcher.publisher.MessageHandlerPublisher;
import io.netty.channel.Channel;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.accept.ApiVersionResolver;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年09月02日
 */
@RestController
@RequestMapping("/message")
public class MessageController {

    @Autowired
    private MessageHandlerPublisher handlerPublisher;

    @Autowired
    private LinkCoreConfig config;


    private AtomicInteger count = new AtomicInteger(1);

    @Autowired
    private MessageSeqAllocator allocator;

    @PostMapping("/send")
    public void messageSend(@RequestBody DefaultMessageDTO dto){
        String id = new ObjectId().toHexString();
        MessageSeqAllocator.SeqResult allocate = allocator.allocate(dto.getChatId(), id);
        long seq = allocate.seq();
        TextData textData = new TextData().setContent(String.valueOf(seq));
        dto.setData(textData);
        dto.setId(id);
        dto.setSeq((int) seq);

        DefaultMessageInfo entity = dto.toEntity();

        List<Channel> channels = this.config.getSessionManager().getChannel(dto.getSndId());
        Channel channel = channels.stream().filter(item -> {
            return item != null && item.isActive();
        }).findFirst().get();

        boolean success = this.handlerPublisher.publishWithHash(entity,channel);
    }

}
