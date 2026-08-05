package com.link.im.handler.group.call;

import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.im.entity.data.CallParticipant;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 通话未接通即结束：全员拒接 / 全员超时 / 主叫在接通前取消。
 *
 * <p>TRTC 会给通话里的每一端都抛这个事件，所以主叫和各被叫都会上报。
 * 这里把上报者自己置为终态，再触发结算——结算逻辑会发现所有人都已终态，
 * 于是把记录落成 GROUP_NOT_CONNECTED（无人接听）。
 *
 * <p>为什么不直接把整通通话标成未接通：因为「未接通」的判定要看是否有人
 * 真正进过房（join_time > 0）。存在这样的边界情况——A 接通了、随后所有人
 * 陆续离开，最后一个离开的端也可能收到 NOT_CONNECTED，此时应落成
 * 「已结束」而非「无人接听」。交给基类的 settleIfFinished 统一判定更稳妥。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkGroupRtcNotConnectedHandler extends BaseGroupCallHandler implements GroupRTCEventHandler {

    @Override
    public GroupRTCEventType event() {
        return GroupRTCEventType.NOT_CONNECTED;
    }

    @Override
    public void handler(LinkRtcGroupCall call, Channel channel) {
        // 上报者自己退出。若他此前已是终态（比如先报过 REJECT），条件更新会落空，符合预期
        advanceParticipant(call, CallParticipant.LEFT);
        settleIfFinished(call);
    }
}
