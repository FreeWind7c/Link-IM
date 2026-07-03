package com.link.common.im.data;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月27日
 */

@Data
@Accessors(chain = true)
public class LinkChatSession {

    private String chatId;

    private int type;

    private String title;

    private String avatar;

    private String ownerId;

    private String targetId;

    private int unreadCount;

    private int lastReadSeq;

    private int lastMsgSeq;

    private String lastMsgSummary;

    private long lastMsgTime;

    private boolean showTop;

    private boolean silence;

    private boolean hidden;

    private boolean active;


}
