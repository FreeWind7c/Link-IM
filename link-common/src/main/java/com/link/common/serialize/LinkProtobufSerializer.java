package com.link.common.serialize;

import com.link.common.serialize.service.LinkSerializer;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月15日
 */
public class LinkProtobufSerializer implements LinkSerializer {
    @Override
    public byte[] serialize(Object obj) {
        return new byte[0];
    }

    @Override
    public Object deserialize(byte[] body, Class<?> c) {
        return null;
    }
}
