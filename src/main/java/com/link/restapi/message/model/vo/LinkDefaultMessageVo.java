package com.link.restapi.message.model.vo;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Data
@Accessors(chain = true)
public class LinkDefaultMessageVo {

    private String id;

    private int seq;

    private int type;

    private String chatId;

    private String sndId;

    private String rcvId;

    private int state;

    private BaseData data;

    private long timestamp;
}
