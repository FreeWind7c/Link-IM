package com.link.im.handler;

import com.link.common.core.event.EventType;
import com.link.common.core.model.messge.ForwardMessage;
import com.link.common.core.model.messge.LinkSingleForward;
import com.link.common.core.model.messge.RcvInfo;
import com.link.common.util.id.ChatIdGenerator;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.EventHandler;
import com.link.core.session.service.LinkSession;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.processor.borad.LinkGroupBroadcaster;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月15日
 */
@Slf4j
@Component
public class LinkSingleForwardEventHandler extends BasePlatFormMongoService<BaseMessage> implements EventHandler {

    /** 单次转发最多携带多少条消息。前端已限制，后端再校验一遍防逆向绕过。 */
    private static final int MAX_FORWARD_MESSAGES = 100;

    /** 单次转发最多发给多少个接收者。约束的是扩散量（落库/ seq 分配 = 接收者×消息），保护 Mongo/Redis。 */
    private static final int MAX_FORWARD_RECEIVERS = 10;

    /**
     * 单条消息内容的最大字节数（UTF-8）。约束单条大小、并配合条数上限界定一次转发的总量：
     * MAX_MESSAGE_BYTES × MAX_FORWARD_MESSAGES = 16K × 100 = 1.6MB（最坏值）。
     * 不做分块：每个接收者的整批消息始终打成“一帧”发出。前端解码器未设帧长上限，可收大帧。
     * ⚠️ 但服务器接入层的 maxFrameLength 必须 ≥ 1.6MB，否则“客户端→服务器”的这条转发请求
     *    本身就会因超过帧限被服务器拒收；为此已把 LinkCoreConfig.maxFrameLength 调到 2MB。
     * 注意必须按“字节”限而不是“字符”：中文 1 字 3 字节，按字符限会被 CJK 击穿。
     */
    private static final int MAX_MESSAGE_BYTES = 16 * 1024;

    @Override
    public EventType event() {
        return EventType.SINGLE_FORWARD;
    }

    @Override
    public Class<?> bodyClass() {
        return LinkSingleForward.class;
    }


    @Autowired
    private MessageSeqAllocator messageSeqAllocator;


    @Autowired
    private LinkCoreConfig config;

    // 原代码是自己注入 LinkPackDataEncoder + 手写 “按 EventLoop 分组广播” 的那段逻辑，
    // 但那段存在“多个 channel 共享同一个 ByteBuf / 只 write 不 flush”等致命 bug。
    // 仓库里 LinkGroupBroadcaster.broadcast 已经是经过验证的正确广播实现
    // （每个 EventLoop 只提交一个任务、per-channel 各自 retainedDuplicate、write 全部后统一 flush、
    //   还带 isActive/isWritable 背压判断），这里直接复用，不再重复造轮子。
    @Autowired
    private LinkGroupBroadcaster groupBroadcaster;

    @Override
    public void handler(Object obj, Channel channel) {
        LinkSingleForward forward = (LinkSingleForward) obj;
        log.info("[转发] 收到转发请求 sndId={}, sessionId={}, msgCount={}, rcvCount={}, channel={}",
                forward.getSndId(), forward.getSessionId(),
                forward.getMessages() == null ? null : forward.getMessages().size(),
                forward.getRcvId() == null ? null : forward.getRcvId().size(),
                channel.id());

        if (!stringValidator(forward.getSndId(), forward.getSessionId())
                || forward.getMessages() == null || forward.getMessages().isEmpty()
                || forward.getRcvId() == null || forward.getRcvId().isEmpty()) {
            log.warn("[转发] 基础校验不通过被丢弃: sndId={}, sessionId={}, messages={}, rcvId={}",
                    forward.getSndId(), forward.getSessionId(), forward.getMessages(), forward.getRcvId());
            return;
        }

        // 规模与单条大小校验：前端本会拦截，这里再兜一道，防止有人绕过前端逆向直接打接口。
        // 不合规一律静默丢弃 + warn 日志（与现有非法输入处理一致，不回错误 ACK）。
        // 这三条限制共同保证“每个接收者的整批消息能塞进一帧”，从而不需要分块逻辑。
        if (forward.getMessages().size() > MAX_FORWARD_MESSAGES
                || forward.getRcvId().size() > MAX_FORWARD_RECEIVERS
                || hasOversizedMessage(forward.getMessages())) {
            log.warn("转发请求超限被拒: sndId={}, msgCount={}, rcvCount={}",
                    forward.getSndId(), forward.getMessages().size(), forward.getRcvId().size());
            return;
        }

        // 原代码在这里先把 messages 转成一份 BaseMessage 列表再传下去，
        // 但转发是“每个接收者各自一份”，共享同一批对象会互相覆盖（见 forwardMessage 里的说明），
        // 所以改为把原始 ForwardMessage 直接传下去，由 forwardMessage 为每个接收者单独生成副本。
        forwardMessage(forward.getSndId(), forward.getRcvId(), forward.getMessages());
    }

    /** 是否存在内容超过单条字节上限的消息。按 UTF-8 字节数量，避免 CJK 按字符计被击穿。 */
    private boolean hasOversizedMessage(List<ForwardMessage> messages) {
        for (ForwardMessage m : messages) {
            String data = m.getData();
            if (data != null && data.getBytes(StandardCharsets.UTF_8).length > MAX_MESSAGE_BYTES)
                return true;
        }
        return false;
    }

    private void forwardMessage(String sndId, List<RcvInfo> receivers, List<ForwardMessage> forwards) {
        List<DefaultMessageInfo> defaultToInsert = new ArrayList<>();
        List<GroupMessageInfo> groupToInsert = new ArrayList<>();

        BulkOperations chatBulk = getMongoTemplate().bulkOps(BulkOperations.BulkMode.UNORDERED, ChatSession.class);
        boolean hasChatUpdate = false;
        List<Delivery> deliveries = new ArrayList<>(receivers.size());

        for (RcvInfo receiver : receivers) {
            // 单聊共享 chatId = nextId(发送者userId, 接收者userId)，与建会话/普通单聊算法一致，
            // 两端得到同一个 chatId，接收方才能按 chatId 命中本地会话。
            // 注意第二个参数必须是接收者「用户 id」(getRcvId)，不是会话 id(getChatId)——传错会算出对不上的 chatId。
            String chatId = ChatIdGenerator.nextId(sndId, receiver.getRcvId());
            List<BaseMessage> copies = new ArrayList<>(forwards.size());
            for (ForwardMessage fm : forwards) {
                BaseMessage msg = new DefaultMessageInfo().generateMessage(fm, sndId);
                MessageSeqAllocator.SeqResult allocate = messageSeqAllocator.allocate(chatId, msg.getId().toHexString());
                msg.setSeq((int) allocate.seq())
                        .setType(fm.getType())
                        .setRcvId(new ObjectId(receiver.getRcvId()))
                        .setChatId(chatId);
                copies.add(msg);
                if (msg instanceof GroupMessageInfo group)
                    groupToInsert.add(group);
                else
                    defaultToInsert.add((DefaultMessageInfo) msg);
            }
            if (copies.isEmpty())
                continue;

            copies.sort(Comparator.comparingInt(BaseMessage::getSeq));
            BaseMessage last = copies.get(copies.size() - 1);
            chatBulk.updateOne(
                    eq(where(col(ChatSession::getChatId)).is(chatId)),   // TODO last.getData()
                    update().set(col(ChatSession::getLastMsgSummary), MessageType.summaryOf(last.getType(), null))
                            .set(col(ChatSession::getLastMsgType), last.getType())
                            .set(col(ChatSession::getLastMsgTime), now())
                            .set(col(ChatSession::getLastMsgSeq), last.getSeq())
            );
            hasChatUpdate = true;


            List<Channel> channels = new ArrayList<>();
            List<LinkSession> sessions = this.config.getSessionManager().getSession(receiver.getRcvId());
            if (sessions != null) {
                for (LinkSession s : sessions) {
                    if (s.getChannel() != null && s.getChannel().isActive())
                        channels.add(s.getChannel());
                }
            }
            deliveries.add(new Delivery(channels, copies));
        }


        if (!defaultToInsert.isEmpty())
            this.getMongoTemplate().insert(defaultToInsert, DefaultMessageInfo.COLLECTION_NAME);
        if (!groupToInsert.isEmpty())
            this.getMongoTemplate().insert(groupToInsert, GroupMessageInfo.COLLECTION_NAME);
        if (hasChatUpdate)
            chatBulk.execute();

        // ---- 5. 再投递：把“这个接收者的多条副本”打成一个 JSON 数组、一帧推给他自己的 channel ----
        // 不分块：每个接收者稳定一帧发出（body 是消息数组）。入口已限制 ≤100 条、单条 ≤16K。
        // 仍是“按接收者各打各的包”——每人的 rcvId/chatId/seq 都不同，绝不能共享一份序列化结果。
        for (Delivery delivery : deliveries) {
            if (delivery.channels().isEmpty())
                continue;   // 接收者离线：消息已落库，重连时补拉即可
            byte[] body = this.config.getLinkSerializer().serialize(delivery.messages());
            this.groupBroadcaster.broadcast(EventType.SINGLE_FORWARD, delivery.channels(), body);
        }
    }

    /**
     * 一个接收者的投递单元：他自己的在线 channel + 他自己的消息副本。
     * 用它把“生成/落库”与“推送”两个阶段解耦，保证先持久化再投递。
     */
    private record Delivery(List<Channel> channels, List<BaseMessage> messages) {
    }

}
