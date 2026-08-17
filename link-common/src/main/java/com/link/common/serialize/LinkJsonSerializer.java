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
        this(typeHierarchyAdapters, null);
    }

    /**
     * @param typeHierarchyAdapters 同上，对目标类及其所有子类生效
     * @param typeAdapters          精确匹配的适配器（目标类 -> 适配器实例），只对该类本身生效，
     *                              可为 null。适用于没有继承体系、也不希望波及其他类型的场景。
     */
    public LinkJsonSerializer(Map<Class<?>, Object> typeHierarchyAdapters,
                              Map<Class<?>, Object> typeAdapters) {
        GsonBuilder builder = new GsonBuilder();
        if (typeHierarchyAdapters != null) {
            typeHierarchyAdapters.forEach(builder::registerTypeHierarchyAdapter);
        }
        if (typeAdapters != null) {
            typeAdapters.forEach(builder::registerTypeAdapter);
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
