package com.link.common.core.model.friend;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月21日
 *
 * 加好友通知用的线上 DTO（纯 POJO）。由 UserInfo 实体到本 DTO 的映射放在调用方（link-api），
 * 以保持 link-common 不反向依赖 link-im 的实体。
 */
@Data
@Accessors(chain = true)
public class LinkFriend {

    private String userId;

    private String nickname;

    private String avatar;
}
