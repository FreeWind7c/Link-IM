package com.link.im.entity.redpack;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.List;

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
        // 定时扫过期红包用：按 status + expireTime 查，字段没加 @Field，def 用驼峰
        @CompoundIndex(name = "idx_status_expire", def = "{'status':1,'expireTime':1}")
})
public class RedPacket extends BaseEntity {

    public static final String COLLECTION_NAME = "red_packet";

    /** 发送者 */
    private ObjectId senderId;

    /** 所属会话 */
    private ObjectId chatId;

    // 0普通红包 1拼手气红包
    private int bizType;

    /** 红包总金额 */
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /** 红包总份数 */
    private int totalCount;

    /** 剩余可抢金额（抢一次原子扣减，扣穿即为 bug） */
    private BigDecimal remainAmount = BigDecimal.ZERO;

    /** 剩余可抢份数（抢一次原子 -1，抢到 0 即抢完，绝不为负） */
    private int remainCount;

    /** 0 进行中 1 已抢完 2 已过期退款 */
    private int status;

    /** 过期时间（毫秒时间戳），到点没抢完则退回剩余金额 */
    private long expireTime;

    /** 祝福语 */
    private String blessing;

    public List<RedPacketItem> children;


}
