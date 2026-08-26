package com.link.im.vo;

import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.vo.base.BaseMessageVO;
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
public class DefaultMessageVO extends BaseMessageVO {
    /**
     * 判空同 {@link GroupMessageVO#from}：本方法在落库投递路径上，
     * 任一 ObjectId 为 null 都会 NPE 并让消息静默投不出去。
     */
    public static DefaultMessageVO from(DefaultMessageInfo item) {

        DefaultMessageVO vo = new DefaultMessageVO();
        BeanUtils.copyProperties(item,vo);
        vo.setId(item.getId() == null ? null : item.getId().toHexString());
        vo.setSndId(item.getSndId() == null ? null : item.getSndId().toHexString());
        vo.setRcvId(item.getRcvId() == null ? null : item.getRcvId().toHexString());
        return vo;
    }

    public DefaultMessageInfo to() {
        DefaultMessageInfo entity = new DefaultMessageInfo();
        BeanUtils.copyProperties(this,entity);
        entity.setId(this.getId() == null ? null : new ObjectId(this.getId()));
        entity.setSndId(this.getSndId() == null ? null : new ObjectId(this.getSndId()));
        entity.setRcvId(this.getRcvId() == null ? null : new ObjectId(this.getRcvId()));
        return entity;
    }

    public int getMessageType(){
        return 1;
    }
}
