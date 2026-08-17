package com.link.im.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * MongoDB data 字段更新工具类。
 * 用于在 data 字段为 JSON String 时，支持嵌套字段更新。
 */
public class DataFieldUpdateHelper {

    private static final Gson GSON = new Gson();

    /**
     * 更新 data 字段中的某个嵌套字段。
     *
     * @param dataJson 原始 data JSON 字符串
     * @param fieldPath 要更新的字段路径，如 "status"、"amount" 等
     * @param value 新值
     * @return 更新后的 JSON 字符串
     */
    public static String updateDataField(String dataJson, String fieldPath, Object value) {
        if (dataJson == null || dataJson.isEmpty()) {
            return dataJson;
        }

        try {
            JsonObject jsonObject = JsonParser.parseString(dataJson).getAsJsonObject();

            // 处理简单字段（不支持嵌套路径，如需要可以扩展）
            if (value instanceof String) {
                jsonObject.addProperty(fieldPath, (String) value);
            } else if (value instanceof Number) {
                jsonObject.addProperty(fieldPath, (Number) value);
            } else if (value instanceof Boolean) {
                jsonObject.addProperty(fieldPath, (Boolean) value);
            } else {
                jsonObject.add(fieldPath, GSON.toJsonTree(value));
            }

            return GSON.toJson(jsonObject);
        } catch (Exception e) {
            // 解析失败，返回原值
            return dataJson;
        }
    }
}
