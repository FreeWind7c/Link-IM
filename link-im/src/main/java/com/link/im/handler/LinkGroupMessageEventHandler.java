package com.link.im.handler;

import com.link.common.core.event.EventType;
import com.link.common.core.model.ack.LinkAck;
import com.link.core.config.LinkCoreConfig;
import com.link.core.event.handler.EventHandler;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.constants.group.GroupRoleConstant;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.data.Mention;
import com.link.im.entity.data.TextData;
import com.link.im.entity.group.GroupMember;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.QuoteRef;
import com.link.im.entity.message.type.MessageType;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.processor.message.LinkGroupMessageProcessor;
import com.link.im.service.LinkRedisService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Slf4j
@Component
public class LinkGroupMessageEventHandler extends BasePlatFormMongoService<GroupMessageInfo> implements EventHandler {

    @Autowired
    private LinkCoreConfig config;

    @Autowired
    private MessageSeqAllocator messageSeqAllocator;


    @Autowired
    private LinkGroupMessageProcessor messageProcessor;

    @Autowired
    private LinkRedisService redisService;

    /** 共享线程池：承接 seq 分配之后的重活（@校验、落库、会话更新、扇出推送），并行处理不受分区限制。 */
    @Autowired
    @Qualifier("IMExecutor")
    private ThreadPoolTaskExecutor workerPool;

    @Override
    public EventType event() {
        return EventType.GROUP_MESSAGE;
    }

    @Override
    public Class<?> bodyClass() {
        return GroupMessageInfo.class;
    }

    /** 按 chatId 分区：同一群会话的消息恒定进同一单线程，保证 seq 分配严格有序。 */
    @Override
    public String partitionKey(Object obj) {
        return ((GroupMessageInfo) obj).getChatId();
    }

    @Override
    public void handler(Object obj, Channel channel) {
        // 本方法运行在分区单线程上（同一 chatId 串行）。只做最小临界区：分配 seq。
        // 分配完立刻把重活甩给共享池并行，避免慢 IO 占用分区线程。
        GroupMessageInfo message = (GroupMessageInfo) obj;
        MessageSeqAllocator.SeqResult seqResult = messageSeqAllocator.allocate(message.getChatId(), message.getId());

        if (seqResult.duplicate())
        {
            this.config.getLinkSender().send(EventType.ACK,channel,message.getSeq());
            return;
        }
        message.setSeq((int) seqResult.seq());

        // seq 已定，后续均与顺序无关：落库按 seq 查、会话更新有 lt 守卫自愈、推送端上按 seq 排序。
        this.workerPool.submit(() -> processHeavy(message, channel));
    }

    /** 重活：在共享线程池并行执行。@校验、引用校验、入库、会话摘要更新、@列表、回 ACK、扇出推送。 */
    private void processHeavy(GroupMessageInfo message, Channel channel) {
        // @ 提及校验：剔除非群成员、按权限收敛 @全体、清洗悬空占位符，防伪造 @。入库前完成，落库即为可信数据。
        sanitizeMentions(message);
        // 引用校验：回查原消息、校验可引用性、用服务端快照覆盖客户端传值，防伪造。入库前完成。
        sanitizeQuote(message);
        LinkAck linkAck = new LinkAck(message.getId(),message.getChatId(),message.getSeq());
        try {
            this.insert(message);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // Redis 去重漏网的重发:DB 已有,捞出已存在那条,回 ACK 即可,不再转发
            this.config.getLinkSender().send(EventType.ACK, channel, linkAck);
            return;
        }

        Query eq = eq(
                where(col(ChatSession::getChatId)).is(message.getChatId())
                        .and(col(ChatSession::getLastMsgSeq)).lt(message.getSeq())
        );

        Update update = update()
                .set(col(ChatSession::getLastMsgSummary), MessageType.summaryOf(message.getType(), message.getData()))
                .set(col(ChatSession::getLastMsgType), message.getType())
                .set(col(ChatSession::getLastMsgTime), now())
                .set(col(ChatSession::getLastMsgSeq), message.getSeq());


        this.getMongoTemplate().updateFirst(eq, update,ChatSession.class);
        // 被 @ 成员写入未读@我列表(与在线无关)。放在扇出前，保证离线成员重登后仍能看到@提醒。
        pushAtList(message);
        this.config.getLinkSender().send(EventType.ACK,channel,linkAck);
        this.messageProcessor.processor(message,channel);
    }

    /** atList 上限：保留最早 10 条未读@我。 */
    private static final int AT_LIST_MAX = 10;

    /**
     * 把本条消息的 seq 追加进被 @ 成员的 {@link ChatSessionMember#getAtList()}（$push + $slice 保留最早 10 条）。
     *
     * <p>@全体 → 覆盖全群（排除发送者）；@个人 → 仅 mentions 里的成员（排除发送者）。
     * 非文本、或无 @ 的消息直接跳过，不产生任何写入。与收件人在线状态无关。
     */
    private void pushAtList(GroupMessageInfo message) {
        if (!(message.getData() instanceof TextData td)) {
            return;
        }
        boolean hasMentions = td.getMentions() != null && !td.getMentions().isEmpty();
        if (!td.isMentionAll() && !hasMentions) {
            return;
        }

        Criteria criteria = where(col(ChatSessionMember::getChatId)).is(message.getChatId());
        boolean validSnd = ObjectId.isValid(message.getSndId());
        if (td.isMentionAll()) {
            // @全体：覆盖全群，排除发送者自己（同一字段只加一次 ne，避免 Criteria 重复键报错）
            if (validSnd) {
                criteria.and(col(ChatSessionMember::getOwnerId)).ne(new ObjectId(message.getSndId()));
            }
        } else {
            // @个人：仅命中 mentions 里的 userId（sanitizeMentions 已保证都是本群成员），并剔除发送者
            List<ObjectId> targetIds = new ArrayList<>();
            for (Mention m : td.getMentions()) {
                if (m != null && ObjectId.isValid(m.getUserId())
                        && !(validSnd && m.getUserId().equals(message.getSndId()))) {
                    targetIds.add(new ObjectId(m.getUserId()));
                }
            }
            if (targetIds.isEmpty()) {
                return;
            }
            criteria.and(col(ChatSessionMember::getOwnerId)).in(targetIds);
        }

        Update update = update()
                .push(col(ChatSessionMember::getAtList))
                .slice(AT_LIST_MAX)
                .each(message.getSeq());
        this.getMongoTemplate().updateMulti(eq(criteria), update, ChatSessionMember.class);
    }

    /** 匹配正文里的 @ 占位符：{@userId} 或 {@all}。 */
    private static final Pattern MENTION_PLACEHOLDER = Pattern.compile("\\{@(\\w+)}");

    /**
     * 校验并重建引用快照。客户端只传 {@code {msgId, seq, chatId}} 定位字段，其余快照由此回查补全，
     * 防止客户端伪造引用内容或引用不可引用类型（红包/语音等）。
     *
     * <p>以下情况清空 quote（当作普通消息处理，不拒收整条）：定位字段缺失、跨会话引用、
     * 原消息不存在、原消息类型不可引用。
     */
    private void sanitizeQuote(GroupMessageInfo message) {
        QuoteRef quote = message.getQuote();
        if (quote == null) {
            return;
        }
        if (quote.getSeq() <= 0 || !message.getChatId().equals(quote.getChatId())) {
            message.setQuote(null);
            return;
        }

        Query srcQuery = eq(
                where(col(AbstractMessage::getChatId)).is(message.getChatId())
                        .and(col(AbstractMessage::getSeq)).is(quote.getSeq())
        );
        GroupMessageInfo source = this.findOne(srcQuery);
        if (source == null || !MessageType.isQuotable(source.getType())) {
            message.setQuote(null);
            return;
        }
        message.setQuote(QuoteRef.of(source));
    }

    private void sanitizeMentions(GroupMessageInfo message) {
        if (!(message.getData() instanceof TextData td)) {
            return;
        }
        boolean hasMentions = td.getMentions() != null && !td.getMentions().isEmpty();
        if (!hasMentions && !td.isMentionAll()) {
            return;
        }

        // 1) mentions 成员校验：非本群成员剔除。成员集走 Redis 缓存，避免额外 DB 查询。
        Set<String> validIds = new HashSet<>();
        if (hasMentions) {
            Set<String> memberIds = this.redisService.getChatMemberIds(message.getChatId());
            Iterator<Mention> it = td.getMentions().iterator();
            while (it.hasNext()) {
                Mention m = it.next();
                if (m == null || m.getUserId() == null
                        || memberIds == null || !memberIds.contains(m.getUserId())) {
                    it.remove();
                    continue;
                }
                validIds.add(m.getUserId());
            }
        }

        // 2) @全体权限：仅群主/管理员。无权限置回 false。仅在确有 mentionAll 时才查角色（罕见路径）。
        if (td.isMentionAll() && !canMentionAll(message.getChatId(), message.getSndId())) {
            td.setMentionAll(false);
        }

        // 3) 清洗悬空占位符：userId 不在最终 mentions、或 {@all} 但无 @全体权限的，去掉占位形式当普通文本。
        String content = td.getContent();
        if (content != null && content.indexOf('{') >= 0) {
            boolean allowAll = td.isMentionAll();
            Matcher matcher = MENTION_PLACEHOLDER.matcher(content);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String id = matcher.group(1);
                boolean keep = "all".equals(id) ? allowAll : validIds.contains(id);
                // 保留合法占位符原样；悬空的把花括号去掉，退化成 "@id" 纯文本，不再被端上识别为可点提及
                matcher.appendReplacement(sb, Matcher.quoteReplacement(keep ? matcher.group() : "@" + id));
            }
            matcher.appendTail(sb);
            td.setContent(sb.toString());
        }
    }

    private boolean canMentionAll(String chatId, String sndId) {
        if (!ObjectId.isValid(sndId)) {
            return false;
        }
        Query memberQuery = eq(
                where(col(ChatSessionMember::getChatId)).is(chatId)
                        .and(col(ChatSessionMember::getOwnerId)).is(new ObjectId(sndId))
        );
        memberQuery.fields().include(col(ChatSessionMember::getTargetId));
        ChatSessionMember sessionMember = this.getMongoTemplate().findOne(memberQuery, ChatSessionMember.class);
        if (sessionMember == null || sessionMember.getTargetId() == null) {
            return false;
        }

        Query roleQuery = eq(
                where(col(GroupMember::getGroupId)).is(sessionMember.getTargetId())
                        .and(col(GroupMember::getUserId)).is(new ObjectId(sndId))
        );
        roleQuery.fields().include(col(GroupMember::getRole));
        GroupMember member = this.getMongoTemplate().findOne(roleQuery, GroupMember.class);
        return member != null
                && (member.getRole() == GroupRoleConstant.CREATOR
                || member.getRole() == GroupRoleConstant.ADMINISTRATOR);
    }
}
