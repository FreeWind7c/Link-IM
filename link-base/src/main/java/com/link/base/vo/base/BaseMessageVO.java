package com.link.base.vo.base;

import com.link.base.entity.base.BaseData;
import com.link.base.entity.message.quote.QuoteRef;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Data
@Accessors(chain = true)
public class BaseMessageVO {
    private String id;

    private int seq;

    private int type;

    private String chatId;

    private String sndId;

    private String rcvId;

    private int state;

    private String data;

    private BaseData baseData;

    private long timestamp;

    private QuoteRef quote;
}
