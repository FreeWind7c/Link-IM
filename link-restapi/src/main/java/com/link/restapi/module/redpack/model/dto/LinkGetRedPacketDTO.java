package com.link.restapi.module.redpack.model.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class LinkGetRedPacketDTO {

    private String packetId;

    /** 查看人。红包详情含每个人抢到的金额，必须校验其在该会话内，不能只凭 packetId 就查 */
    private String userId;

}
