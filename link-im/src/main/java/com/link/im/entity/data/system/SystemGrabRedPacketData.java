package com.link.im.entity.data.system;

import com.link.im.constants.sys.SystemActionKeys;
import com.link.im.entity.data.SystemData;
import io.netty.handler.codec.spdy.SpdyHttpResponseStreamIdHandler;
import lombok.Data;
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
