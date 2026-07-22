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
     * 业务类型 1=普通转账，2=红包转账，3=商户转账，4=链上转账，5=后台转账,6提现,7.退款
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




}
