package com.link.im.common.mongo;

import org.springframework.data.mongodb.core.mapping.Field;

import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从可序列化 lambda（如 UserInfo::getAccount）解析出 MongoDB 字段名。
 * 解析结果做缓存，避免重复反射/序列化开销。
 */
public final class LinkLambdaUtil {

    private LinkLambdaUtil() {
    }

    /** key 用 lambda 的实现类名，同一个方法引用每次是同一个生成类 */
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    public static <T, R> String fieldName(SFunction<T, R> fn) {
        SerializedLambda lambda = resolve(fn);
        return CACHE.computeIfAbsent(lambda.getImplClass() + "#" + lambda.getImplMethodName(),
                k -> toFieldName(lambda));
    }

    private static SerializedLambda resolve(SFunction<?, ?> fn) {
        try {
            Method writeReplace = fn.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            return (SerializedLambda) writeReplace.invoke(fn);
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析 lambda 字段名，请确认传入的是方法引用，如 实体::getXxx", e);
        }
    }

    private static String toFieldName(SerializedLambda lambda) {
        String methodName = lambda.getImplMethodName();
        String property = methodToProperty(methodName);
        // 若属性上有 @Field 注解则用注解里的名字，否则用属性名
        try {
            Class<?> implClass = Class.forName(lambda.getImplClass().replace('/', '.'));
            java.lang.reflect.Field field = findField(implClass, property);
            if (field != null) {
                Field anno = field.getAnnotation(Field.class);
                if (anno != null && !anno.value().isEmpty()) {
                    return anno.value();
                }
            }
        } catch (ClassNotFoundException ignored) {
            // 拿不到类就退化为属性名
        }
        return "id".equals(property) ? "_id" : property;
    }

    private static String methodToProperty(String methodName) {
        String name;
        if (methodName.startsWith("get")) {
            name = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            name = methodName.substring(2);
        } else {
            // 不是标准 getter，直接当属性名用
            return methodName;
        }
        if (name.isEmpty()) {
            return methodName;
        }
        // 首字母小写；若前两位都是大写（如 URL）则保持原样，遵循 JavaBean 规范
        if (name.length() > 1 && Character.isUpperCase(name.charAt(1))) {
            return name;
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    private static java.lang.reflect.Field findField(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }
        }
        return null;
    }
}
