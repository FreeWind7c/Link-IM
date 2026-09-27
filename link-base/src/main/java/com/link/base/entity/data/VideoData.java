package com.link.base.entity.data;

import com.link.base.entity.base.BaseData;
import com.link.base.entity.message.type.MessageType;
import com.link.base.provider.MessageTypeProvider;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@Accessors(chain = true)
public class VideoData extends BaseData implements MessageTypeProvider {

    private String url;

    @Override
    public int getMessageType() {
        return MessageType.VIDEO_MESSAGE.getType();
    }
}
