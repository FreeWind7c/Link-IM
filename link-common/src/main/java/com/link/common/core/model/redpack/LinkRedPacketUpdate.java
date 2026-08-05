package com.link.common.core.model.redpack;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 红包状态变更事件（EventType.UPDATE_RED_PACKET）。
 *
 * <p>每被领取一次就推一条，群聊靠它增量刷新红包气泡（谁领了、还剩多少、是否已派完），
 * 从而不必给每次领取都往会话里插一条系统消息——500 人群、100 份红包会刷屏 100 条。
 * 单聊仍保留系统消息（「XX 领取了你的红包」是单聊里用户预期看到的一条会话记录）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class LinkRedPacketUpdate {

    private String chatId;

    private String messageId;

    private String packetId;

    /** 与 RedPacket.status 对齐：0 进行中 1 已抢完 2 已过期退款 */
    private int status;

    /** 本次领取人；过期退款事件为 null */
    private String claimantId;

    /** 变更后的剩余份数，端上直接渲染「还剩 N 个」，不必再回查接口 */
    private int remainCount;

    /** 变更后的剩余金额 */
    private BigDecimal remainAmount;

}
