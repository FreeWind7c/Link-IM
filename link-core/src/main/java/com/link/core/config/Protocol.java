package com.link.core.config;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 *
 * 服务端对外承载协议：原生 TCP 或 WebSocket。
 * 两者底层都复用同一套 PackData 编解码与业务 handler，仅 pipeline 前端不同。
 */
public enum Protocol {

    /** 原生 TCP 长连接 */
    TCP,

    /** WebSocket（二进制帧 BinaryWebSocketFrame 承载 PackData 字节流） */
    WEBSOCKET
}
