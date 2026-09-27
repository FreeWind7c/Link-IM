package com.link.base.dto.message;

import com.link.base.dto.base.BaseMessageDTO;
import com.link.base.entity.message.GroupMessageInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月15日
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class GroupMessageDTO extends BaseMessageDTO {

    public GroupMessageInfo converterEntity() {
        GroupMessageInfo message = new GroupMessageInfo();
        BeanUtils.copyProperties(this, message);
        message.setId(new ObjectId(this.getId()));
        message.setSndId(new ObjectId(this.getSndId()));
        message.setRcvId(new ObjectId(this.getRcvId()));
        return message;
    }
}
