package com.link.im.entity.redpack;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * 抢红包记录（每抢中一次一条）
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
@Document(collection = RedPacketRecord.COLLECTION_NAME)
@CompoundIndexes({
        // 一人一红包：同一红包同一用户只能有一条记录。唯一键防并发重复抢。
        // 字段没加 @Field，def 必须用驼峰 packetId / userId
        @CompoundIndex(name = "idx_packet_user", def = "{'packetId':1,'userId':1}", unique = true)
})
public class RedPacketRecord extends BaseEntity {

    public static final String COLLECTION_NAME = "red_packet_record";

    /** 所属红包 id */
    private ObjectId packetId;

    // 子红包id
    private ObjectId childrenId;

    /** 抢到的用户 */
    private ObjectId userId;

    // 0已抢到 2已下发
    private int status;

    // 预计完成下发时间
    private long dutTime;

    /** 抢到的金额 */
    private BigDecimal amount = BigDecimal.ZERO;
}
