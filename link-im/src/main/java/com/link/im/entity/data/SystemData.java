package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.type.MessageType;
import com.link.im.provider.MessageTypeProvider;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@Accessors(chain = true)
public class SystemData extends BaseData implements MessageTypeProvider {

    private final int action;

    public SystemData(int action){
        this.action = action;
    }

    @Override
    public int getMessageType() {
        return MessageType.SYSTEM_MESSAGE.getType();
    }
}
