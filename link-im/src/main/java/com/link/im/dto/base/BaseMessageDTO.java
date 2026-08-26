package com.link.im.dto.base;

import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.quote.QuoteRef;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 消息 DTO 基类，用于网络传输和业务逻辑处理。
 * 字段类型全部使用 String，与前端 JSON 格式一致。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月15日
 */
@Data
@ToString
@Accessors(chain = true)
public abstract class BaseMessageDTO {

    private String id;

    private int seq;

    private int type;

    private String chatId;

    private String sndId;

    private String rcvId;

    private int state;

    private BaseData data;

    private long timestamp;

    /**
     * 引用的消息快照。为 null 表示非引用消息（老数据天然兼容）。
     * 客户端只传 {@code {msgId, seq, chatId}} 定位字段，其余快照由服务端回查补全。
     */
    private QuoteRef quote;
}
