package com.link.im.entity.redpack;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 红包拆分出来的单份。
 *
 * <p><b>它不是 MongoDB 文档</b>，只是 Redis 预扣队列（List）里的载荷：
 * 发红包时一次性拆好入队，抢红包 rightPop 一次即预占一份。
 *
 * <p>刻意不继承 BaseEntity、也不带 redPacketId：
 * <ul>
 *   <li>{@code _id / create_time / update_time} 是 Mongo 文档才需要的，这里没有任何读取方</li>
 *   <li>父红包 id 就写在 Redis 的 key 上（{@code red_packet:{packetId}}），再存一遍纯属冗余</li>
 * </ul>
 * 队列里可能有几百个元素，每个字段都会乘以份数写进 Redis，能省则省。
 *
 * <p>「谁抢走了多少」完整记录在 {@link RedPacketRecord}，本类不承担审计职责。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月19日
 */
@Data
@Accessors(chain = true)
public class RedPacketItem {

    /** 这一份的金额 */
    private BigDecimal amount = BigDecimal.ZERO;

    /** 手气最佳 */
    private boolean best;

}
