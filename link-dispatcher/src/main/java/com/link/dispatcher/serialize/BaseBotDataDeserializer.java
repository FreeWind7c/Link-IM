package com.link.dispatcher.serialize;

import com.google.gson.Gson;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.link.base.entity.base.BaseBotData;
import com.link.base.entity.data.boot.BotAnswerData;
import com.link.base.entity.data.boot.BotQuestionData;

import java.lang.reflect.Type;

/**
 * BaseBotData 的多态反序列化器。
 *
 * <p>问题背景：AIBotMessageDTO.data / AIUserQuestionDTO.data 的声明类型都是 BaseBotData（父类），
 * Gson 按声明类型还原，会得到一个空的 BaseBotData，导致 BotQuestionData.question 等子类字段
 * 全部丢失，调用方强转时抛 ClassCastException。
 *
 * <p>与 {@link BaseMessageDeserializer} 的区别：那边靠消息体里的 type 字段反查子类，
 * 而 bot 消息已经去掉了 type，改为「只看 data 自身的结构」判定：
 * <ul>
 *   <li>含 question / media → {@link BotQuestionData}（用户提问，可纯文本、可带媒体）</li>
 *   <li>含 answer → {@link BotAnswerData}（AI 答复）</li>
 * </ul>
 *
 * <p>注意这是判定依据的唯一来源，新增 BaseBotData 子类时必须在 {@link #resolve} 里补一条分支，
 * 否则会直接抛 JsonParseException（而不是静默还原成错误类型）。
 *
 * <p>通过 registerTypeHierarchyAdapter 注册，对 BaseBotData 的所有子类生效。只实现了
 * JsonDeserializer、没有实现 JsonSerializer，所以写出方向不受影响：Gson 会退回到运行时
 * 类型的反射适配器，answer / question 字段照常序列化。
 */
public class BaseBotDataDeserializer implements JsonDeserializer<BaseBotData> {

    /** 不带本适配器的纯 Gson，用于还原具体子类，避免递归调用自身。 */
    private static final Gson PLAIN = new Gson();

    @Override
    public BaseBotData deserialize(JsonElement json, Type typeOfT,
                                   JsonDeserializationContext context) throws JsonParseException {

        if (json == null || json.isJsonNull()) {
            return null;
        }

        // 字段本来就声明成具体子类时无需猜，直接按目标类型还原
        if (typeOfT instanceof Class<?> clazz && clazz != BaseBotData.class) {
            return (BaseBotData) PLAIN.fromJson(json, clazz);
        }

        JsonObject obj = json.getAsJsonObject();
        return PLAIN.fromJson(obj, resolve(obj));
    }

    /**
     * 按 data 自身携带的字段判定具体子类。
     *
     * @throws JsonParseException 结构无法识别时抛出，避免静默还原成空对象后在业务层才炸
     */
    private static Class<? extends BaseBotData> resolve(JsonObject obj) {
        if (obj.has("question") || obj.has("media")) {
            return BotQuestionData.class;
        }
        if (obj.has("answer")) {
            return BotAnswerData.class;
        }
        throw new JsonParseException("无法识别的 bot data 结构：" + obj);
    }
}
