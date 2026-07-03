package com.link.restapi.message.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 「取消息上下文」请求：以 centerSeq 为中心，取其本身 + 前后各 size 条。
 * 用于引用消息点击跳转——只知道一个目标 seq，不知道两端。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月02日
 */
@Data
@Accessors(chain = true)
public class LinkAroundMessageDto {

    private String chatId;

    /** 拉取者 uid。群聊必填：用于按其 blackoutGaps 过滤被踢期间不可见的消息。 */
    private String userId;

    private int sessionType;

    /** 定位中心，一般传引用消息里的 quote.seq。 */
    private int centerSeq;

    /** 中心两侧各取多少条。<=0 时服务端回退默认值。 */
    private int size;
}
