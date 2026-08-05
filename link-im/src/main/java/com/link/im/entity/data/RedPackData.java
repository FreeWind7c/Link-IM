package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.redis.core.convert.PathIndexResolver;

import java.math.BigDecimal;
import java.util.List;

/**
 * 红包消息（RED_PACK_MESSAGE=1005）的 data 体，单聊红包与群拼手气红包共用。
 *
 * <p>只放端上渲染红包气泡要用的字段：红包 id（点开时按它调抢红包接口）、总额、
 * 状态、祝福语。剩余份数 / 剩余金额是高频变动的，不进消息体，由端上按 id 实时查。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class RedPackData extends BaseData {

    /**
     * 红包 id（RedPacket 主键的 hex 串）。
     *
     * <p>存 String 而非 ObjectId：本类会随消息经 Jackson 序列化过 MQ 推到端上，
     * ObjectId 序列化后是一个对象（timestamp/date/...），消费端还原不回来。
     */
    private String id;

    /** 红包总金额 */
    private BigDecimal amount;

    /** 0 进行中 1 已抢完 2 已过期退款，与 RedPacket.status 对齐 */
    private int status;

    /** 祝福语 */
    private String blessing;

    /** 红包总份数。单聊红包恒为 1，拼手气红包用于渲染「共 N 个」 */
    @Field("total_count")
    private int totalCount;

    @Field("claimant_ids")
    private List<String> claimantIds;
}
