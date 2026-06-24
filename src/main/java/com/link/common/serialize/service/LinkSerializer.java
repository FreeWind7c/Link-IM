package com.link.common.serialize.service;

import io.netty.buffer.ByteBuf;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public interface LinkSerializer {

    byte[] serialize(Object obj);

    Object deserialize(ByteBuf b, Class<?> c);

}
