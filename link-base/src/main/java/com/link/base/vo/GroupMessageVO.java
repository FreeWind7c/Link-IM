package com.link.base.vo;

import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.vo.base.BaseMessageVO;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Data
@Accessors(chain = true)
public class GroupMessageVO extends BaseMessageVO {
    /**
     * 群消息的 rcvId 允许为 null——群消息没有单一接收者，
     * 群通知（persistGroupNotice）更是显式置空。这里必须判空，
     * 否则本方法在落库投递路径上会 NPE，消息静默投不出去。
     */
    public static GroupMessageVO from(BaseMessage message) {
        GroupMessageInfo item = (GroupMessageInfo) message;
        GroupMessageVO vo = new GroupMessageVO();
        BeanUtils.copyProperties(item,vo);
        vo.setId(item.getId() == null ? null : item.getId().toHexString());
        vo.setSndId(item.getSndId() == null ? null : item.getSndId().toHexString());
        vo.setRcvId(item.getRcvId() == null ? null : item.getRcvId().toHexString());
        return vo;
    }

    public GroupMessageInfo to() {
        GroupMessageInfo entity = new GroupMessageInfo();
        BeanUtils.copyProperties(this,entity);
        entity.setId(this.getId() == null ? null : new ObjectId(this.getId()));
        entity.setSndId(this.getSndId() == null ? null : new ObjectId(this.getSndId()));
        entity.setRcvId(this.getRcvId() == null ? null : new ObjectId(this.getRcvId()));
        return entity;
    }

    public int getMessageType(){
        return 2;
    }
}
