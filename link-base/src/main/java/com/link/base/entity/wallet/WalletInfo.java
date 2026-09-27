package com.link.base.entity.wallet;

import com.link.base.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月07日
 */
@Data
@Accessors(chain = true)
@Document(collection = WalletInfo.COLLECTION_NAME)
public class WalletInfo extends BaseEntity {
    public static final String COLLECTION_NAME = "wallet_info";


    @Field("user_id")
    private ObjectId userId;

    private String password;

    private BigDecimal balance;

    // 是否禁止使用
    private boolean disabled;

}
