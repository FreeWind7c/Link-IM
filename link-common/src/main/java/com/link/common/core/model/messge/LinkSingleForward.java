package com.link.common.core.model.messge;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月15日
 */
@Data
@Accessors(chain = true)
public class LinkSingleForward {

    private String sndId;

    private List<RcvInfo> rcvId;

    private String sessionId;

    private List<ForwardMessage> messages;

}
