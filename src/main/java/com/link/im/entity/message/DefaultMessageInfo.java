package com.link.im.entity.message;

import com.link.restapi.message.model.vo.LinkDefaultMessageVo;
import lombok.ToString;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */

@ToString(callSuper = true)
@Document(DefaultMessageInfo.COLLECTION_NAME)
public class DefaultMessageInfo extends AbstractMessage {
    public static final String COLLECTION_NAME = "default_message_queue";

    public LinkDefaultMessageVo createVo() {
        LinkDefaultMessageVo vo = new LinkDefaultMessageVo();
        BeanUtils.copyProperties(this,vo);
        return vo;
    }
}
