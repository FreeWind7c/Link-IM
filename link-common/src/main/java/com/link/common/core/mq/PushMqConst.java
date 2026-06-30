package com.link.common.core.mq;

/**
 * 推送 MQ 的拓扑常量：交换机 / 路由名。两端（发布端 restapi、消费端 link-consumer）共享，
 * 放在 link-common 避免各写一份字符串导致对不上。
 *
 * <p>采用 fanout 广播：所有 Netty 接入节点各自绑一个临时队列到同一交换机，每条推送指令
 * 被所有节点收到，仅持有目标用户 session 的节点真正下发，其余 no-op。这样多节点部署时
 * 不会因「共享队列竞争消费」把指令投给没有该用户连接的节点而丢失。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
public final class PushMqConst {

    private PushMqConst() {
    }

    // 网络事件交换机
    public static final String LINK_EVENT_EXCHANGE = "link.event.exchange";

    // 单点推送队列
    public static final String DIRECT_EVENT_PUSH_QUEUE = "direct.event.push.queue";

    public static final String DIRECT_EVENT_PUSH_KEY = "direct.event.push.key";



}
