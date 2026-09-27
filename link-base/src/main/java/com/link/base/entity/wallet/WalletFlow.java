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
 * @CreateTime: 2026年07月07日
 */
@Data
@Accessors(chain = true)
@Document(collection = WalletFlow.COLLECTION_NAME)
public class WalletFlow  extends BaseEntity {
    public static final String COLLECTION_NAME = "wallet_flow";


    @Field("user_id")
    private ObjectId userId;

    private BigDecimal amount = BigDecimal.ZERO;
    /**
     * 收支类型，0：支出，1：收入
     */
    @Field("in_out")
    private int inOut;
    /**
     * 业务类型 1=红包，2=转账,3=充值,4=提现,5=退款,6=后台充值
     */
    @Field("biz_type")
    private int bizType;
    /**
     * 业务详情id
     */
    @Field("biz_detail_id")
    private String bizDetailId;
    /**
     * 备注
     */
    private String remark;

    private long timestamp;
}
