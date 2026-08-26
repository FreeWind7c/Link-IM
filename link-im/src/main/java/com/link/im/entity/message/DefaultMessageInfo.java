package com.link.im.entity.message;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.link.common.core.model.messge.ForwardMessage;
import com.link.common.core.model.messge.LinkSingleForward;
import com.link.common.core.model.messge.RcvInfo;
import com.link.common.util.id.ChatIdGenerator;
import com.link.common.util.id.LinkID;
import com.link.im.dto.message.DefaultMessageDTO;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.base.BaseEntity;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.data.ImageData;
import com.link.im.entity.data.TextData;
import com.link.im.entity.data.VideoData;
import com.link.im.entity.message.quote.QuoteRef;
import com.link.im.entity.message.type.MessageType;
import com.link.im.vo.DefaultMessageVO;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */

@Data
@ToString(callSuper = true)
@Document(DefaultMessageInfo.COLLECTION_NAME)
@Accessors(chain = true)
@CompoundIndexes({
        @CompoundIndex(name = "idx_chat_seq", def = "{'chat_id':1,'seq':1}"),
        @CompoundIndex(name = "idx_chat_time", def = "{'chat_id':1,'timestamp':1}"),
        @CompoundIndex(name = "idx_chat_type_time", def = "{'chat_id':1,'type':1,'timestamp':1}"),
        @CompoundIndex(name = "idx_call_id", def = "{'data.call_id':1}", sparse = true),
})
public class DefaultMessageInfo extends BaseMessage {
    public static final String COLLECTION_NAME = "default_message_queue";

    public BaseMessage generateMessage(ForwardMessage message,String sndId){
        BaseMessage abstractMessage  =  new DefaultMessageInfo().setId(new ObjectId())
                .setData(message.getData())
//                .setData(converterBaseData(message))
                .setState(1)
                .setQuote(null)
                .setSndId(new ObjectId(sndId))
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


    public DefaultMessageVO toVo() {
        DefaultMessageVO vo = new DefaultMessageVO();
        BeanUtils.copyProperties(this,vo);
        vo.setId(this.getId().toHexString());
        vo.setSndId(this.getSndId().toHexString());
        vo.setRcvId(this.getRcvId().toHexString());
        return vo;
    }
}
