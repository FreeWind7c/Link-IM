package com.link.im.entity.message;

import com.link.im.entity.data.NoticeData;
import com.link.im.entity.message.type.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@ToString(callSuper = true)
@Document(collection = GroupMessageInfo.COLLECTION_NAME)
public class GroupMessageInfo extends AbstractMessage {
    public static final String COLLECTION_NAME = "group_message_queue";



    public static GroupMessageInfo create(String s, long seq, MessageType noticeMessage, String chatId, String inviterUserId, String id, int i, NoticeData noticeData, long now) {
        GroupMessageInfo messageInfo = new GroupMessageInfo();
        return messageInfo;
    }
}
