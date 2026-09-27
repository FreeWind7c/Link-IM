package com.link.restapi.module.ai.controller;

import com.google.gson.Gson;
import com.link.restapi.module.ai.model.dto.LinkResolveChatSessionDTO;
import com.link.restapi.module.ai.service.LinkAIChatSessionService;
import com.link.restapi.utils.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 会话控制器
 * 提供会话名称解析等 AI 相关接口
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月27日
 */
@RestController
@RequestMapping("/ai/chat")
public class LinkAIChatSessionController {

    @Autowired
    private LinkAIChatSessionService service;

    /**
     * 根据会话名称解析出 chatId
     *
     * 用于 AI 助手根据用户的自然语言（如"统计我在A群的发言"）
     * 找到对应的会话ID
     *
     * @param dto 包含 userId, sessionName, sessionType
     * @return chatId 和相关信息
     */
    @PostMapping("/resolve-session")
    public ApiResult resolveChatSession(@RequestBody LinkResolveChatSessionDTO dto) {
        System.out.println("resolveChatSession:" + new Gson().toJson(dto));
        return service.resolveChatSession(dto);
    }
}
