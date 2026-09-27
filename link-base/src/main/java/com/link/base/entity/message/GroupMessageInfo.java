package com.link.base.entity.message;

import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.data.NoticeData;
import com.link.base.entity.message.type.MessageType;
import com.link.base.vo.GroupMessageVO;
import lombok.ToString;
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
@ToString(callSuper = true)
@Document(collection = GroupMessageInfo.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "idx_chat_seq", def = "{'chat_id':1,'seq':1}"),
        @CompoundIndex(name = "idx_chat_time", def = "{'chat_id':1,'timestamp':1}"),
        @CompoundIndex(name = "idx_chat_type_time", def = "{'chat_id':1,'type':1,'timestamp':1}"),
        // 群通话降级关联：被叫未拿到 userData 时靠 data.call_id 定位通话记录。
        // sparse=true —— 只有群通话记录才有这个字段，其余消息不进索引。
        @CompoundIndex(name = "idx_call_id", def = "{'data.call_id':1}", sparse = true),
})
public class GroupMessageInfo extends BaseMessage {

    public static final String COLLECTION_NAME = "group_message_queue";

    public static GroupMessageInfo create(String s, long seq, MessageType noticeMessage, String chatId, String inviterUserId, String id, int i, NoticeData noticeData, long now) {
        GroupMessageInfo messageInfo = new GroupMessageInfo();
        return messageInfo;
    }

    public GroupMessageVO toVo() {
        GroupMessageVO vo = new GroupMessageVO();
        BeanUtils.copyProperties(this,vo);
        vo.setId(this.getId().toHexString());
        vo.setSndId(this.getSndId().toHexString());
        vo.setRcvId(this.getRcvId().toHexString());
        return vo;
    }

}
