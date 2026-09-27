package com.link.base.entity.wallet;

import com.link.base.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
@Document(collection = WalletRefund.COLLECTION_NAME)
@CompoundIndexes({
        // 字段没加 @Field，Mongo 按属性名原样存，索引 def 必须用驼峰 userId / bizDetailId
        @CompoundIndex(name = "idx_user_business", def = "{'userId':1,'bizDetailId':1}",unique = true),

})public class WalletRefund extends BaseEntity {
    public static final String COLLECTION_NAME = "wallet_refund";

    private ObjectId userId;

    // 业务流水号
    private ObjectId bizDetailId;

    // 0为退款 1已退款
    private int status;

    // 1.提现退库按 2.红包退款 3.转账退款
    private int bizType;

    private BigDecimal amount = BigDecimal.ZERO;

    // 预计完成时间
    private long dueTime;

}
