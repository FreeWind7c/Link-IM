package com.link.im.entity.redpack;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * 抢红包记录（每抢中一次一条）。
 *
 * <p>本记录和「扣库存 + 加钱 + 记流水」在同一个 MongoDB 事务内写入，要么全成要么全无，
 * 因此不存在「已抢到但没下发」的中间态，也就不需要 status/dutTime 两阶段字段和补偿扫描。
 * 下面的唯一索引是同一用户重复领取同一红包的最终防线（Redis 幂等键只是快速路径）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
@Document(collection = RedPacketRecord.COLLECTION_NAME)
@CompoundIndexes({
        // def 里写库中真实字段名（packet_id / user_id 都有 @Field 映射），与本项目其余索引写法一致
        @CompoundIndex(name = "idx_packet_user", def = "{'packet_id':1,'user_id':1}", unique = true)
})
public class RedPacketRecord extends BaseEntity {

    public static final String COLLECTION_NAME = "red_packet_record";

    /** 所属红包 id */
    @Field("packet_id")
    private ObjectId packetId;

    /** 抢到的用户 */
    @Field("user_id")
    private ObjectId userId;

    private boolean best;

    /** 抢到的金额 */
    private BigDecimal amount = BigDecimal.ZERO;
}
