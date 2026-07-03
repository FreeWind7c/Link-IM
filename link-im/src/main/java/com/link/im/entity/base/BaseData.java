package com.link.im.entity.base;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
// data 字段在 AbstractMessage 里声明为 BaseData，跨 MQ 用 Jackson 序列化时若不写类型标记，
// 消费端只能按声明类型 BaseData 还原（空类），子类字段（chatId 等）就会报 UnrecognizedPropertyException。
// restapi 与 consumer 共用同一套 link-im 类，全限定类名一致，用 Id.CLASS 自动带上具体类型即可。
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@Data
@ToString
public class BaseData {



}
