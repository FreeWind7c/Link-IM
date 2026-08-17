package com.link.im.entity.base;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月11日
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
@ToString
@Accessors(chain = true)
public class BaseBotData {
}
