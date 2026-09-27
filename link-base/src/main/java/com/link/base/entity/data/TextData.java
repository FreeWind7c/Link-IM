package com.link.base.entity.data;

import com.link.base.entity.base.BaseData;
import com.link.base.entity.data.message.Mention;
import com.link.base.entity.message.type.MessageType;
import com.link.base.provider.MessageTypeProvider;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class TextData extends BaseData implements MessageTypeProvider {

    private String content;

    /**
     * 结构化提及列表（@某人）。为空表示没有 @ 个人，不影响老逻辑。
     * mentionAll 不在此列出——@全体由 {@link #mentionAll} 单独标记，避免展开全体成员。
     */
    private List<Mention> mentions;

    /** 是否 @ 全体成员。与 {@link #mentions} 可同时存在。仅群主/管理员可置 true，服务端会校验。 */
    private boolean mentionAll;

    @Override
    public int getMessageType() {
        return MessageType.TEXT_MESSAGE.getType();
    }
}
