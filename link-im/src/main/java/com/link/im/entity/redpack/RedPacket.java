package com.link.im.entity.redpack;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * 群拼手气红包（资金池）
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
@Document(collection = RedPacket.COLLECTION_NAME)
@CompoundIndexes({
        // 定时扫过期红包用：按 status + expireTime 查。def 里写库中真实字段名（expire_time 有 @Field 映射）
        @CompoundIndex(name = "idx_status_expire", def = "{'status':1,'expire_time':1}")
})
public class RedPacket extends BaseEntity {

    public static final String COLLECTION_NAME = "red_packet";

    /**
     * 客户端传来的业务唯一流水号，发红包的<b>持久化</b>幂等凭据。
     *
     * <p>Redis 幂等键只是快速拦截，会过期、会因 Redis 重启丢失；真正保证「同一次点击不会扣两次钱」
     * 的是这个字段上的唯一索引。sparse=true 是为了兼容本字段上线前的历史数据（值为 null 的多条不冲突）。
     */
    @Field("biz_detail_id")
    @Indexed(unique = true, sparse = true, name = "idx_biz_detail_id")
    private String bizDetailId;

    /**
     * 承载这个红包的会话消息 id（发红包时与红包同事务写入）。
     *
     * <p>有了它，「更新红包气泡状态」就不必再采信客户端<b>本次请求</b>传上来的 messageId——
     * 那是可伪造的，照着写等于允许任何人往别人的消息里塞 claimant_ids。
     * 过期退款走定时任务，压根没有客户端，也只能靠这个字段找到要置灰的那条消息。
     *
     * <p>当前取值等于 {@link #bizDetailId}（发红包时同源赋值）。<b>但两者不能合并成一个字段</b>：
     * bizDetailId 是幂等凭据，带唯一索引，写入后永不能变——变了就等于放行重复扣款；
     * 本字段是指向会话消息的外键，语义上允许变（后端补发消息、红包换绑消息都会改它）。
     * 现在值相同只是巧合，合并会把这两条互斥的约束绑死。
     */
    @Field("message_id")
    private String messageId;

    /** 发送者 */
    @Field("snd_id")
    private String sndId;

    @Field("rcv_id")
    private String rcvId;

    @Field("chat_id")
    private String chatId;

    // 1普通红包 2拼手气红包
    @Field("biz_type")
    private int bizType;

    /** 红包总金额 */
    @Field("total_amount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 红包总份数 */
    @Field("total_count")
    private int totalCount;

    /** 剩余可抢金额（抢一次原子扣减，扣穿即为 bug） */
    @Field("remain_amount")
    private BigDecimal remainAmount = BigDecimal.ZERO;

    /** 剩余可抢份数（抢一次原子 -1，抢到 0 即抢完，绝不为负） */
    @Field("remain_count")
    private int remainCount;

    /** 0 进行中 1 已抢完 2 已过期退款 */
    private int status;

    /** 过期时间（毫秒时间戳），到点没抢完则退回剩余金额 */
    @Field("expire_time")
    private long expireTime;

    /** 祝福语 */
    private String blessing;


}
