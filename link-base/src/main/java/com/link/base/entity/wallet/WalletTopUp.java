package com.link.base.entity.wallet;

import com.link.base.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
@Document(collection = WalletTopUp.COLLECTION_NAME)
public class WalletTopUp extends BaseEntity {

    public static final String COLLECTION_NAME = "wallet_top_up";
    public static final String MD5_SECRET = "wtu:mdki13#de3jk#0e032n8ws12klgdf3";


    @Field("user_id")
    private ObjectId userId;

    @Field("secret_key")
    private String secretKey;

    private BigDecimal amount = BigDecimal.ZERO;

    // 0兑换中 1已兑换 2已失效 3已冻结 4已退款
    private int status;

}
