package com.link.common.serialize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.link.common.serialize.service.LinkSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
public class LinkJsonSerializer implements LinkSerializer {

    private final Gson gson;

    public LinkJsonSerializer() {
        this(null);
    }

    /**
     * @param typeHierarchyAdapters 需要注册的 type-hierarchy 适配器（基类 -> 适配器实例）。
     *                              用于多态反序列化等场景，可为 null。注册逻辑留在 common 之外，
     *                              避免 common 反向依赖上层模型。
     */
    public LinkJsonSerializer(Map<Class<?>, Object> typeHierarchyAdapters) {
        GsonBuilder builder = new GsonBuilder();
        if (typeHierarchyAdapters != null) {
            typeHierarchyAdapters.forEach(builder::registerTypeHierarchyAdapter);
        }
        this.gson = builder.create();
    }

    @Override
    public byte[] serialize(Object obj) {
        return this.gson.toJson(obj).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Object deserialize(byte[] body, Class<?> c) {
        String json = new String(body, StandardCharsets.UTF_8);
        return this.gson.fromJson(json, c);
    }
}
