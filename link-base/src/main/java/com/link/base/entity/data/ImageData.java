package com.link.base.entity.data;

import com.link.base.entity.base.BaseData;
import com.link.base.entity.message.type.MessageType;
import com.link.base.provider.MessageTypeProvider;
import lombok.Data;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
public class ImageData extends BaseData implements MessageTypeProvider {
    private String url;

    private int width;

    private int height;

    @Override
    public int getMessageType() {
        return MessageType.IMAGE_MESSAGE.getType();
    }
}
