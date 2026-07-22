package com.link.im.entity.wallet;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

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

    private BigDecimal balance;

    private boolean disabled;

}
