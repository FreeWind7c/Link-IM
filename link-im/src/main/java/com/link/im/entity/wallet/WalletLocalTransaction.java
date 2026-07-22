package com.link.im.entity.wallet;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
@Document(collection = WalletLocalTransaction.COLLECTION_NAME)
public class WalletLocalTransaction extends BaseEntity {

    public static final String COLLECTION_NAME = "wallet_local_trx";

    private String trxId;

    // 1.支付 2.提现 3.转账 4.红包
    private int type;

    // 0未完成 1已完成
    private int status;

    // 预计完成时间
    private long dueTime;

    private long timestamp;

}
