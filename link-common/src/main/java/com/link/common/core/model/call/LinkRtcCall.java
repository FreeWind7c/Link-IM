package com.link.common.core.model.call;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Data
@Accessors(chain = true)
public class LinkRtcCall {

    private String sndId;

    private String rcvId;

    private String chatId;

    private String messageId;

    private short eventType;

    // 0语音通话 1视频通话
    private int mediaType;

    private String data;

}
