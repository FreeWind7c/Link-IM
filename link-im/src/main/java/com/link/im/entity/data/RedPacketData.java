package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import com.link.im.entity.message.type.MessageType;
import com.link.im.entity.redpack.RedPacket;
import com.link.im.provider.MessageTypeProvider;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
public class RedPacketData extends BaseData implements MessageTypeProvider {

    /** 红包 ID */
    private String id;

    /** 祝福语 */
    private String blessing;

    /** 红包总金额（创建时快照） */
    private BigDecimal totalAmount;

    /** 红包总份数。单聊红包恒为 1，拼手气红包用于渲染「共 N 个」 */
    private int totalCount;

    /** 剩余可抢金额（抢一次原子扣减，扣穿即为 bug） */
    private BigDecimal remainAmount = BigDecimal.ZERO;

    /** 剩余可抢份数（抢一次原子 -1，抢到 0 即抢完，绝不为负） */
    private int remainCount;

    /** 0 进行中 1 已抢完 2 已过期退款 */
    private int status;

    private List<String> claimantIds = new ArrayList<>();

    public static RedPacketData toData(RedPacket before) {
        RedPacketData data = new RedPacketData();
        BeanUtils.copyProperties(before,data);
        data.setId(before.getId().toHexString());
        data.setClaimantIds(before.getClaimantIds().stream().map(v -> {return v.toHexString();}).collect(Collectors.toList()));
        return data;
    }

    @Override
    public int getMessageType() {
        return MessageType.RED_PACK_MESSAGE.getType();
    }
}

