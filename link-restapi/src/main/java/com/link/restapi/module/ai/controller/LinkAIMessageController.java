package com.link.restapi.module.ai.controller;

import com.google.gson.Gson;
import com.link.restapi.module.ai.model.dto.LinkSearchMessageDTO;
import com.link.restapi.module.message.model.dto.LinkAIPullMessageDTO;
import com.link.restapi.module.message.model.dto.LinkPullMessageByChatDTO;
import com.link.restapi.module.ai.service.LinkAIMessageInfoService;
import com.link.restapi.utils.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 消息控制器
 * 提供消息查询、搜索等 AI 相关接口
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */

@RestController
@RequestMapping("/ai/message")
public class LinkAIMessageController {

    @Autowired
    private LinkAIMessageInfoService messageInfoService;


    @PostMapping("/search-message")
    public ApiResult searchMessage(@RequestBody LinkSearchMessageDTO dto){
        System.out.println("searchMessage:" + new Gson().toJson(dto));
        return messageInfoService.searchMessage(dto);
    }

    @PostMapping("/pull-message")
    public ApiResult pullMessage(@RequestBody LinkAIPullMessageDTO dto){
        return messageInfoService.pullMessage(dto);
    }

    /**
     * 根据 chatId 查询消息记录
     *
     * 用于 AI 助手在解析出 chatId 后，查询该会话的聊天记录
     *
     * @param dto 包含 userId, chatId, limit
     * @return 消息列表
     */
    @PostMapping("/pull-message-by-chat")
    public ApiResult pullMessageByChat(@RequestBody LinkPullMessageByChatDTO dto){
        System.out.println("pullMessageByChat:" + new Gson().toJson(dto));
        return messageInfoService.pullMessageByChat(dto);
    }

    /**
     * 调试接口：检查消息数据
     * 用于排查为什么查询不到消息
     */
    @PostMapping("/debug-messages")
    public ApiResult debugMessages(@RequestBody java.util.Map<String, String> params) {
        String userId = params.get("userId");
        String friendId = params.get("friendId");
        return messageInfoService.debugMessages(userId, friendId);
    }

}