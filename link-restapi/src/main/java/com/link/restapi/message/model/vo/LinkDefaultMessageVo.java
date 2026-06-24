package com.link.restapi.message.model.vo;

import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.DefaultMessageInfo;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.BeanUtils;

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

    /** 由消息实体拷贝出展示 VO。 */
    public static LinkDefaultMessageVo from(DefaultMessageInfo message) {
        LinkDefaultMessageVo vo = new LinkDefaultMessageVo();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }
}
