package com.link.im.entity.redpack;

import com.link.im.entity.base.BaseEntity;
import lombok.Data;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
public class RedPacketItem extends BaseEntity {

    // 父红包id
    private ObjectId redPacketId;

    private BigDecimal amount = BigDecimal.ZERO;

    // 0进行中 1已抢完 2已退款
    private int status;


}
