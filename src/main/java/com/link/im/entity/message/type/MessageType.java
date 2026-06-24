package com.link.im.entity.message.type;


import com.link.im.entity.data.*;
import com.link.im.entity.base.BaseData;

import java.util.HashMap;
import java.util.Map;

public enum MessageType {
    TEXT_MESSAGE(1001, TextData.class, null),
    IMAGE_MESSAGE(1002, ImageData.class, "[图片]"),
    VIDEO_MESSAGE(1003, VideoData.class, "[视频]"),
    VOICE_MESSAGE(1004, VoiceData.class, "[语音]"),
    RED_PACK_MESSAGE(1005, RedPackData.class, "[红包]");

    private final int type;

    /** 该消息类型对应的 data 体具体类，反序列化时据此还原多态字段 */
    private final Class<? extends BaseData> dataClass;

    /** 会话列表摘要的固定标签（如「[图片]」）。文本类为 null，摘要取正文。 */
    private final String summaryLabel;


    MessageType(int type, Class<? extends BaseData> dataClass, String summaryLabel) {
        this.type = type;
        this.dataClass = dataClass;
        this.summaryLabel = summaryLabel;
    }

    public int getType() {
        return this.type;
    }

    public Class<? extends BaseData> getDataClass() {
        return this.dataClass;
    }

    private static final Map<Integer, MessageType> TYPE_INDEX = new HashMap<>();

    static {
        for (MessageType t : values()) {
            TYPE_INDEX.put(t.type, t);
        }
    }

    /**
     * 根据消息体中的 type 字段反查消息类型，未知 type 返回 null。
     */
    public static MessageType fromType(int type) {
        return TYPE_INDEX.get(type);
    }

    /** 会话列表摘要最大展示长度，超出截断并加省略号。 */
    private static final int SUMMARY_MAX_LEN = 30;

    /**
     * 生成会话列表用的「最后一条消息」摘要。
     *
     * <p>文本类取正文（超长截断）；其余类型用固定标签（如「[图片]」「[语音]」），
     * 避免把 URL / 二进制 data 直接塞进列表。未知 type 或数据缺失时返回空串。
     *
     * @param type 消息 type
     * @param data 消息体（文本类需从中取 content）
     * @return 列表可直接展示的摘要字符串
     */
    public static String summaryOf(int type, BaseData data) {
        MessageType mt = fromType(type);
        if (mt == null) {
            return "";
        }
        if (mt == TEXT_MESSAGE) {
            if (data instanceof TextData td && td.getContent() != null) {
                String content = td.getContent();
                return content.length() <= SUMMARY_MAX_LEN
                        ? content
                        : content.substring(0, SUMMARY_MAX_LEN) + "…";
            }
            return "";
        }
        return mt.summaryLabel;
    }
}
