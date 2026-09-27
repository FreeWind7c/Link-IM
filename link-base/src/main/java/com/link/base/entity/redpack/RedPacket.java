package com.link.base.entity.redpack;

import com.link.base.entity.base.BaseEntity;
import com.link.base.provider.MessageData;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        // 定时扫过期红包用：按 status + expireTime 查。def 里写库中真实字段名（expire_time 有 @Field 映射）
        @CompoundIndex(name = "idx_status_expire", def = "{'status':1,'expire_time':1}")
})
public class RedPacket extends BaseEntity implements MessageData {

    public static final String COLLECTION_NAME = "red_packet";

    @Field("biz_detail_id")
    @Indexed(unique = true, sparse = true, name = "idx_biz_detail_id")
    private String bizDetailId;

    @Field("message_id")
    private ObjectId messageId;

    /** 发送者 */
    @Field("snd_id")
    private ObjectId sndId;

    @Field("rcv_id")
    private ObjectId rcvId;

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

    @Field("claimant_ids")
    private List<ObjectId> claimantIds = new ArrayList<>();




    @Override
    public ObjectId getMessageId() {
        return messageId;
    }


}
