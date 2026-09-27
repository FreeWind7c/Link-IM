package com.link.base.dto.message;

import com.google.gson.Gson;
import com.link.base.dto.base.BaseMessageDTO;
import com.link.base.entity.message.DefaultMessageInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月12日
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class DefaultMessageDTO extends BaseMessageDTO {

    /**
     * 转换为 Entity，用于数据库操作
     * 将 String 类型的 ID 转换为 ObjectId
     */
    public DefaultMessageInfo toEntity() {
        DefaultMessageInfo entity = new DefaultMessageInfo();
        entity.setId(new ObjectId(this.getId()));
        entity.setSeq(this.getSeq());
        entity.setType(this.getType());
        entity.setChatId(this.getChatId());
        entity.setSndId(new ObjectId(this.getSndId()));
        entity.setRcvId(new ObjectId(this.getRcvId()));
        entity.setState(this.getState());
        entity.setData(new Gson().toJson(this.getData()));
        entity.setBaseData(this.getData());
        entity.setTimestamp(this.getTimestamp());
        entity.setQuote(this.getQuote());
        return entity;
    }
}
