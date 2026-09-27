package com.link.restapi.module.message.model.vo;

import com.link.base.entity.base.BaseData;
import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.message.quote.QuoteRef;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.beans.BeanUtils;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Data
@Accessors(chain = true)
public class LinkMessageInfoVo {

    private String id;

    private int seq;

    private int type;

    private String chatId;

    private String sndId;

    private String rcvId;

    private int state;

    private String data;

    private long timestamp;

    /** 引用的消息快照，非引用消息为 null。前端据此渲染引用块并支持点击跳转。 */
    private QuoteRef quote;

    /** 由消息实体拷贝出展示 VO。 */
    public static LinkMessageInfoVo from(DefaultMessageInfo message) {
        LinkMessageInfoVo vo = new LinkMessageInfoVo();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }

    public static LinkMessageInfoVo from(GroupMessageInfo message) {
        LinkMessageInfoVo vo = new LinkMessageInfoVo();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }

    public static LinkMessageInfoVo from(BaseMessage message) {
        LinkMessageInfoVo vo = new LinkMessageInfoVo();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }
}
