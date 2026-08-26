package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.type.MessageType;
import com.link.im.provider.MessageTypeProvider;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月19日
 */
@Data
@Accessors(chain = true)
public class FileData extends BaseData implements MessageTypeProvider {

    private String url;

    private String fileName;

    private String suffix;

    private int size;

    @Override
    public int getMessageType() {
        return MessageType.FILE_MESSAGE.getType();
    }
}
