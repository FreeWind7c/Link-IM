package com.link.im.entity.message.quote;

import com.link.im.entity.base.BaseData;
import com.link.im.entity.data.ImageData;
import com.link.im.entity.data.VideoData;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.message.type.MessageType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 引用消息快照。挂在 {@link BaseMessage#getQuote()} 上，为 null 表示非引用消息。
 *
 * <p>为什么存快照而不是只存 msgId：
 * <ul>
 *   <li>加载消息列表时引用预览可直接渲染，不必逐条回查原消息（避免 N+1）；</li>
 *   <li>原消息被撤回/删除后，引用块仍保留当时的内容摘要。</li>
 * </ul>
 *
 * <p>客户端发送时只需带定位字段 {@code {msgId, seq, chatId}}，其余快照字段
 * （type / summary / thumbUrl / sndId）由服务端回查原消息后补全并覆盖，
 * 防止客户端伪造引用内容。
 *
 * <p>会跨 RabbitMQ 推送，Jackson 反序列化需无参构造器，否则报 no Creators。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class QuoteRef {

    /** 被引用消息 id，点击跳转用。 */
    private String msgId;

    /** 被引用消息的 seq —— 跳转定位的核心键，配合 chatId 走 idx_chat_seq 索引取上下文。 */
    private int seq;

    /** 被引用消息所在会话 id（一般同会话，存下来更稳）。 */
    private String chatId;

    /** 原发送者 id。昵称不存快照，由前端按 sndId 从成员缓存解析（与 @ 提及渲染一致）。 */
    private String sndId;

    /** 原消息类型，前端据此渲染引用预览（文本正文 / [图片] / [视频]）。 */
    private int type;

    /** 引用预览文案，服务端用 {@link MessageType#summaryOf} 生成，前端直接展示。 */
    private String summary;

    /** 图片/视频引用的缩略图 url，其余类型为 null。 */
    private String thumbUrl;

    /**
     * 由回查到的原消息构建快照。调用前须确保 source 类型 {@link MessageType#isQuotable} 为 true。
     */
    public static QuoteRef of(BaseMessage source, BaseData data) {
        QuoteRef ref = new QuoteRef();
        ref.setMsgId(source.getId().toHexString());
        ref.setSeq(source.getSeq());
        ref.setChatId(source.getChatId());
        ref.setSndId(source.getSndId().toHexString());
        ref.setType(source.getType());
        ref.setSummary(MessageType.summaryOf(source.getType(), data));


        if (data instanceof ImageData img) {
            ref.setThumbUrl(img.getUrl());
        } else if (data instanceof VideoData video) {
            ref.setThumbUrl(video.getUrl());
        }
        return ref;
    }
}
