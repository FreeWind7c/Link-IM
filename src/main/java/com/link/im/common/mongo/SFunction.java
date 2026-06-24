package com.link.im.common.mongo;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的 getter 函数式接口，用于 lambda 取字段名（如 UserInfo::getAccount）。
 * 必须继承 Serializable，运行时才能拿到 SerializedLambda 解析出方法名。
 *
 * @param <T> 实体类型
 * @param <R> 字段类型
 */
@FunctionalInterface
public interface SFunction<T, R> extends Function<T, R>, Serializable {
}
