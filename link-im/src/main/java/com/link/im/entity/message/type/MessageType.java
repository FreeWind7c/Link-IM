package com.link.im.entity.message.type;


import com.link.im.entity.data.*;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.data.NoticeData;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public enum MessageType {
    SYSTEM_MESSAGE(1000, SystemData.class, null, false),

    TEXT_MESSAGE(1001, TextData.class, null, true),
    IMAGE_MESSAGE(1002, ImageData.class, "[图片]", true),
    VIDEO_MESSAGE(1003, VideoData.class, "[视频]", true),
    VOICE_MESSAGE(1004, VoiceData.class, "[语音]", false),
    RED_PACK_MESSAGE(1005, RedPackData.class, "[红包]", false),
    NOTICE_MESSAGE(1006, NoticeData.class, null, false),
    RTC_CALL_MESSAGE(1007,CallData.class,null,false);

    private final int type;

    /** 该消息类型对应的 data 体具体类，反序列化时据此还原多态字段 */
    private final Class<? extends BaseData> dataClass;

    /** 会话列表摘要的固定标签（如「[图片]」）。文本类为 null，摘要取正文。 */
    private final String summaryLabel;

    /** 是否允许被引用。红包/语音/通知等不可引用；转账后续接入也置 false。 */
    private final boolean quotable;


    MessageType(int type, Class<? extends BaseData> dataClass, String summaryLabel, boolean quotable) {
        this.type = type;
        this.dataClass = dataClass;
        this.summaryLabel = summaryLabel;
        this.quotable = quotable;
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

    public boolean isQuotable() {
        return this.quotable;
    }

    /**
     * 该 type 的消息是否允许被引用。未知 type 视为不可引用。
     * 服务端收到带 quote 的消息时据此校验，不受前端绕过影响。
     */
    public static boolean isQuotable(int type) {
        MessageType mt = fromType(type);
        return mt != null && mt.quotable;
    }

    /** 会话列表摘要最大展示长度，超出截断并加省略号。 */
    private static final int SUMMARY_MAX_LEN = 30;

    /** 匹配正文里的 @ 占位符：{@userId} 或 {@all}。 */
    private static final Pattern MENTION_PLACEHOLDER = Pattern.compile("\\{@(\\w+)}");

    /**
     * 把文本正文里的 @ 占位符还原成可读昵称，供会话列表摘要展示。
     *
     * <p>{@code {@userId}} → {@code @昵称}（取 mentions 里对应 userId 的 name 快照，缺失则退回 {@code @userId}）；
     * {@code {@all}} → {@code @所有人}。无占位符的普通文本原样返回。
     */
    private static String renderMentions(TextData td) {
        String content = td.getContent();
        if (content == null || content.indexOf('{') < 0) {
            return content == null ? "" : content;
        }
        Map<String, String> nameById = new HashMap<>();
        if (td.getMentions() != null) {
            for (Mention m : td.getMentions()) {
                if (m != null && m.getUserId() != null) {
                    nameById.put(m.getUserId(), m.getName());
                }
            }
        }
        Matcher matcher = MENTION_PLACEHOLDER.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String id = matcher.group(1);
            String replacement;
            if ("all".equals(id)) {
                replacement = "@所有人";
            } else {
                String name = nameById.get(id);
                replacement = "@" + (name != null ? name : id);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

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
                // 正文里的 @ 占位符先还原成昵称，否则列表会显示 {@1001} 这种原始占位符
                String content = renderMentions(td);
                return content.length() <= SUMMARY_MAX_LEN
                        ? content
                        : content.substring(0, SUMMARY_MAX_LEN) + "…";
            }
            return "";
        }
        if (mt == RTC_CALL_MESSAGE){
            CallData call = (CallData) data;
            if (call.getMediaType() == 0)
                return "[语音通话]";
            else if (call.getMediaType() == 1)
                return "[视频通话]";
            else
                return "[位置消息类型]";
        }
        return mt.summaryLabel;
    }
}
