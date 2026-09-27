package com.link.base.entity.data.system;

import com.link.common.constants.sys.SystemActionKeys;
import com.link.base.entity.data.SystemData;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Getter
@Setter
@Accessors(chain = true)
public class SystemGrabRedPacketData extends SystemData {

    // 发送者ID
    private String sndId;


    private String packetMessageId;

    // 领取者ID
    private String claimantId;

    private String icon = "🧧";


    public SystemGrabRedPacketData() {
        super(SystemActionKeys.GRAB_RED_PACKET);
    }
}
