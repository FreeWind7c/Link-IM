package com.link.im.handler;

import com.link.common.channel.DefaultChannelAttributeKeys;
import com.link.common.core.event.EventType;
import com.link.common.core.event.GroupRTCEventType;
import com.link.common.core.model.call.LinkRtcGroupCall;
import com.link.core.event.facotry.GroupRTCEventHandlerFactory;
import com.link.core.event.handler.EventHandler;
import com.link.core.event.handler.GroupRTCEventHandler;
import com.link.core.session.service.LinkSession;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 群通话（TRTC）状态同步入口，对应 action={@code RTC_GROUP_CALL=1020}。
 *
 * <p>与 {@link LinkRtcEventHandler}（1019，自建 WebRTC 信令转发）并列，
 * 但职责完全不同：这里收到的是「TRTC 侧已经发生的事」，服务端只负责落库记账。
 *
 * <p>本类做两件分发前的公共校验：
 * <ol>
 *   <li><b>身份校验</b>：用连接上的登录态覆盖 sndId。上报字段全部来自客户端，
 *       不校验的话用户可以伪造他人的通话状态（比如把别人标记成「已拒接」）。</li>
 *   <li><b>关联键校验</b>：messageId 与 callId 至少要有一个，
 *       否则这条上报无法归属到任何一条通话记录，直接丢弃。</li>
 * </ol>
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@Component
public class LinkRtcGroupEventHandler implements EventHandler {

    @Autowired
    private GroupRTCEventHandlerFactory factory;

    @Override
    public EventType event() {
        return EventType.RTC_GROUP_CALL;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkRtcGroupCall.class;
    }

    @Override
    public void handler(Object obj, Channel channel) {
        LinkRtcGroupCall call = (LinkRtcGroupCall) obj;

        // 1. 身份校验：以连接上的登录态为准，不信任客户端传来的 sndId
        LinkSession session = (LinkSession) channel
                .attr(AttributeKey.valueOf(DefaultChannelAttributeKeys.SESSION)).get();
        if (session == null || !session.isAuth() || session.getSessionId() == null) {
            log.warn("群通话上报被拒：连接未认证 -> {}", channel.id());
            return;
        }
        String loginUserId = session.getSessionId();
        if (call.getSndId() != null && !loginUserId.equals(call.getSndId())) {
            log.warn("群通话上报的 sndId({}) 与登录用户({}) 不一致，已按登录用户纠正",
                    call.getSndId(), loginUserId);
        }
        call.setSndId(loginUserId);

        // 2. 关联键校验：两个都没有的话无法归属到任何通话记录
        boolean hasKey = (call.getMessageId() != null && !call.getMessageId().isEmpty())
                || (call.getCallId() != null && !call.getCallId().isEmpty());
        if (!hasKey) {
            log.warn("群通话上报缺少 messageId 与 callId，丢弃 -> user={} event={}",
                    loginUserId, call.getEventType());
            return;
        }

        // targetId 缺省时按上报人自己算（自身状态类事件前端可能省略）
        if (call.getTargetId() == null || call.getTargetId().isEmpty()) {
            call.setTargetId(loginUserId);
        }

        GroupRTCEventType type = GroupRTCEventType.fromAction(call.getEventType());
        GroupRTCEventHandler eventHandler = factory.getEventHandler(type);
        if (eventHandler == null) {
            log.error("找不到此群通话事件 -> {}", call.getEventType());
            return;
        }
        eventHandler.handler(call, channel);
    }
}
