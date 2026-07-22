package com.link.im.entity.wallet;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.swing.plaf.PanelUI;
import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
@Document(collection = WalletWithdraw.COLLECTION_NAME)
@CompoundIndexes({
        // 注意:字段没加 @Field,Mongo 按属性名原样存,索引 def 必须用驼峰
        @CompoundIndex(name = "idx_user_withdraw", def = "{'userId':1,'withdrawId':1}", unique = true)
})

public class WalletWithdraw extends BaseEntity {

    public static final String COLLECTION_NAME = "wallet_withdraw";

    private ObjectId userId;

    private String withdrawId;   // ← 新增:客户端幂等键


    private BigDecimal amount = BigDecimal.ZERO;

    // 0未打款 1已打款 2打款失败,已退款
    private int status;

    private String remake;

    // 预计完成时间
    private long dueTime;
}
