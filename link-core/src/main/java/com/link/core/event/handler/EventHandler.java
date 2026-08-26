package com.link.core.event.handler;

import com.link.common.core.event.EventType;
import io.netty.channel.Channel;

public interface EventHandler {

    EventType event();


    Class<?> bodyClass();

    public void handler(Object obj, Channel channel);

    default String partitionKey(Object obj) {
        return null;
    }

    /**
     * 是否就地在 Netty eventLoop 上同步执行，不提交任何线程池。
     *
     * <p>默认 false —— 绝大多数事件都要碰 IO（Mongo / Redis），在 eventLoop 上做慢操作会
     * 卡住挂在同一个 eventLoop 上的所有连接，必须甩给线程池。
     *
     * <p>返回 true 的前提极严：handler 必须是<b>纯内存、微秒级、无锁竞争</b>的操作。
     * 目前只有心跳和 ACK 满足——它们本质是控制面信号，不该和数据面重活挤同一条队列：
     * IMExecutor 是全用户共享的 FIFO，被消息洪水灌满时，排在后面的心跳迟迟执行不到，
     * {@code session.lastHeartbeatTime} 就一直是旧值；而 IdleStateHandler 跑在 eventLoop 上
     * （没被阻塞）准点触发检查，读到过期的时间戳，于是把明明在正常发心跳的在线用户判定为
     * 掉线踢掉（见 IdleHandler#userEventTriggered）。存活探针绝不能和"它本该幸存于其慢速"
     * 的那些工作共享队列。
     *
     * <p>覆写为 true 的 handler 里<b>永远不要</b>加 DB / RPC 调用，否则会阻塞整个 eventLoop。
     */
    default boolean inlineOnEventLoop() {
        return false;
    }

}
