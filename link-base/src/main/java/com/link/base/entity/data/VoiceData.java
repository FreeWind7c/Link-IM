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
public class VoiceData extends BaseData implements MessageTypeProvider {

    private String type = "voice";

    @Override
    public int getMessageType() {
        return MessageType.VOICE_MESSAGE.getType();
    }
}
