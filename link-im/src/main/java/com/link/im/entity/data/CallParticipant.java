package com.link.im.entity.data;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * 群通话参与者状态，嵌在 {@link CallData#getParticipants()} 里。
 *
 * <p>状态流转由 TRTC 上报事件驱动：
 * <pre>
 *   INVITED ──ACCEPT/USER_ENTER──→ JOINED ──USER_LEAVE/HANG_UP──→ LEFT
 *      ├──REJECT──────→ REJECTED
 *      ├──NO_RESPONSE─→ NO_RESPONSE
 *      └──LINE_BUSY───→ LINE_BUSY
 * </pre>
 *
 * <p>状态<b>只能单向前进</b>，不允许回退（见 status 字段注释），
 * 因为上报可能乱序到达——已经 JOINED 的人不该被一条迟到的 RINGING 打回 INVITED。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Data
@Accessors(chain = true)
public class CallParticipant {

    /** 参与者用户 ID */
    @Field("user_id")
    private String userId;

    /**
     * 参与状态：0=已邀请未应答 1=已加入 2=已离开 3=拒接 4=超时未接 5=忙线。
     *
     * <p>注意：更新时必须用「状态优先级」保护，只允许向前推进。
     * 例如 USER_LEAVE 迟到于下一通电话的 INVITED 时不能覆盖，
     * 具体见各 handler 里的条件更新（用 status 做前置条件）。
     */
    private int status;

    /** 加入时间（毫秒），未加入为 0 */
    @Field("join_time")
    private long joinTime;

    /** 离开时间（毫秒），未离开为 0 */
    @Field("leave_time")
    private long leaveTime;

    /** 是否为发起人 */
    private boolean initiator;

    // ---------- 状态常量 ----------

    /** 已邀请，振铃中 */
    public static final int INVITED = 0;
    /** 已接听并进房 */
    public static final int JOINED = 1;
    /** 已离开（正常挂断或中途退出） */
    public static final int LEFT = 2;
    /** 主动拒接 */
    public static final int REJECTED = 3;
    /** 超时未接听 */
    public static final int NO_RESPONSE = 4;
    /** 忙线 */
    public static final int LINE_BUSY = 5;

    /**
     * 该状态是否属于「已终结」——即这个人已经不在通话里了。
     * 用于判断整通通话是否该结束（全员终结即结束）。
     */
    public static boolean isFinal(int status) {
        return status == LEFT || status == REJECTED
                || status == NO_RESPONSE || status == LINE_BUSY;
    }
}
