package com.link.im.entity.data.message;

import com.link.im.entity.data.TextData;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 一条被 @ 的成员。作为 {@link TextData#getMentions()} 的元素随消息体走 MQ / 持久化。
 *
 * <p>正文里对应位置以占位符 {@code {@userId}} 标记，本对象仅携带 userId（后端识别唯一依据）
 * 与发送时刻的昵称快照 name（渲染兜底：拉不到最新群昵称时用它）。
 *
 * <p><b>约束</b>：本类会作为推送 payload 的一部分走 RabbitMQ，Jackson 反序列化需无参构造器。
 * 仅用 {@code @Data}（保留隐式无参构造器），切勿加 {@code @AllArgsConstructor}，否则消费端报 no Creators。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月01日
 */
@Data
@Accessors(chain = true)
public class Mention {

    /** 被 @ 用户 id，后端识别的唯一依据。 */
    private String userId;

    /** 发送时刻的昵称/群昵称快照，渲染兜底用（拉不到最新昵称时回退到它）。 */
    private String name;
}
