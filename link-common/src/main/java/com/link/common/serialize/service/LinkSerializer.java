package com.link.common.serialize.service;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public interface LinkSerializer {

    byte[] serialize(Object obj);

    /**
     * 反序列化消息 body。入参为纯字节数组而非 ByteBuf，使本接口与 Netty 解耦：
     * “从 ByteBuf 读出 body 字节”是解码器/派发器（接入层）的职责，读出后传 byte[] 即可。
     * 这样 link-common 无需依赖 netty，core 从 ByteBuf、未来从 MQ 拿到的 byte[] 都能复用。
     */
    Object deserialize(byte[] body, Class<?> c);

}
