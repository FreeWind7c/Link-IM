package com.link.common.core.mq;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 跨进程推送指令：HTTP 业务进程（restapi）不持长连接，把「请推给某用户」封成本对象发到 MQ，
 * 由持连的 Netty 进程消费后调 LinkMessageSender 真正 channel.write。
 *
 * <p>本对象本身以 JSON 在 MQ 上传输。难点在 payload 是多态的（LinkFriend / LinkApproveFriend …），
 * 直接放 Object 反序列化端无法还原具体类型，故采用「类型名 + JSON 字符串」自描述：
 * 发布端把业务对象序列化进 {@link #payloadJson} 并记下 {@link #payloadType}，
 * 消费端按 payloadType 还原。eventType 用 short（EventType.action）传输，避免依赖枚举序列化细节。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Data
@Accessors(chain = true)
public class DirectPushCommand {

    private List<String> userId;

    private short eventType;

    private String payloadType;

    private String payloadJson;
}
