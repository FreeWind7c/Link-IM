package com.link.im.entity.message;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.link.common.core.model.messge.ForwardMessage;
import com.link.common.core.model.messge.LinkSingleForward;
import com.link.common.core.model.messge.RcvInfo;
import com.link.common.util.id.ChatIdGenerator;
import com.link.common.util.id.LinkID;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.data.ImageData;
import com.link.im.entity.data.TextData;
import com.link.im.entity.data.VideoData;
import com.link.im.entity.message.type.MessageType;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */

@ToString(callSuper = true)
@Document(DefaultMessageInfo.COLLECTION_NAME)
@Accessors(chain = true)
public class DefaultMessageInfo extends AbstractMessage {
    public static final String COLLECTION_NAME = "default_message_queue";

    public AbstractMessage generateMessage(ForwardMessage message,String sndId){
        AbstractMessage abstractMessage  =  new DefaultMessageInfo().setId(LinkID.nextIdStr())
                .setData(converterBaseData(message))
                .setState(1)
                .setQuote(null)
                .setSndId(sndId)
                .setTimestamp(System.currentTimeMillis());
        return abstractMessage;

    }

    private BaseData converterBaseData(ForwardMessage message) {
        int type = message.getType();
        if (type == MessageType.TEXT_MESSAGE.getType()){
            return new Gson().fromJson(message.getData(), TextData.class);
        }else if (type == MessageType.IMAGE_MESSAGE.getType()){
            return new Gson().fromJson(message.getData(), ImageData.class);
        }else if (type == MessageType.VIDEO_MESSAGE.getType()){
            return new Gson().fromJson(message.getData(), VideoData.class);
        }
        return null;
    }


}
