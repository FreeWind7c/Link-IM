package com.link.im.entity.message;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Data
@Accessors(chain = true)
@Document(collection = GroupMessageInfo.COLLECTION_NAME)
public class GroupMessageInfo extends AbstractMessage {
    public static final String COLLECTION_NAME = "group_message_queue";
}
