package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * 红包消息（RED_PACK_MESSAGE=1005）的 data 体，单聊红包与群拼手气红包共用。
 *
 * <p>只存创建时的静态快照：红包 id、总额、祝福语、总份数。
 * <p><b>不再存储动态字段 status 和 claimantIds</b>：
 * <ul>
 *   <li>status - 从 {@code RedPacket.status} 字段获取</li>
 *   <li>claimantIds - 从 {@code RedPacket.claimantIds} 字段获取</li>
 * </ul>
 * <p>前端通过 UPDATE_RED_PACKET 事件实时同步状态，离线场景调用详情接口查询。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class RedPackData extends BaseData {

    /** 红包 ID */
    private String id;

    /** 红包总金额（创建时快照） */
    private BigDecimal amount;

    /** 祝福语 */
    private String blessing;

    /** 红包总份数。单聊红包恒为 1，拼手气红包用于渲染「共 N 个」 */
    @Field("total_count")
    private int totalCount;

}

