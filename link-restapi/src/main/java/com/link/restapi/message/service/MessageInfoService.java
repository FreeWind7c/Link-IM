package com.link.restapi.message.service;

import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.util.ApiResult;
import com.link.restapi.message.model.dto.LinkAroundMessageDto;
import com.link.restapi.message.model.dto.LinkCompleteMessageDto;
import com.link.restapi.message.model.dto.LinkPullMessageDto;
import com.link.restapi.message.model.vo.LinkMessageInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Slf4j
@Component
public class MessageInfoService extends BasePlatFormMongoService<DefaultMessageInfo> {


    public ApiResult pullMessage(LinkPullMessageDto dto) {
        if (!stringValidator(dto.getChatId()) || !pageValidator(dto.getSkip(),dto.getLimit()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        boolean group = dto.getSessionType() != 1;
        // 群聊必须带 userId，否则无法按其 blackoutGaps 过滤被踢期间的消息
        if (group && !stringValidator(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Criteria criteria = where(col(AbstractMessage::getChatId)).is(dto.getChatId());
        if (group) {
            applyBlackoutGaps(criteria, dto.getChatId(), dto.getUserId());
        }

        Query eq = eq(criteria);
        eq.with(Sort.by(Sort.Direction.DESC,col(AbstractMessage::getTimestamp)));
        eq.skip(dto.getSkip());
        eq.limit(dto.getLimit());

        return !group
                ? ApiResult.success().setData(createVo(this.find(eq)))
                : ApiResult.success().setData(createGroupVo(this.getMongoTemplate().find(eq, GroupMessageInfo.class)));
    }

    private List<LinkMessageInfoVo> createVo(List<DefaultMessageInfo> messages) {
        List<LinkMessageInfoVo> vos = messages.stream().map(LinkMessageInfoVo::from).collect(Collectors.toList());
        return vos;
    }

    private List<LinkMessageInfoVo> createGroupVo(List<GroupMessageInfo> messages) {
        List<LinkMessageInfoVo> vos = messages.stream().map(LinkMessageInfoVo::from).collect(Collectors.toList());
        return vos;
    }

    public ApiResult completeMessage(LinkCompleteMessageDto dto) {
        boolean group = dto.getSessionType() != 1;
        if (group && !stringValidator(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Criteria criteria = where(col(AbstractMessage::getChatId)).is(dto.getChatId())
                .and(col(AbstractMessage::getSeq)).gt(dto.getFrom()).lt(dto.getTo());
        if (group) {
            applyBlackoutGaps(criteria, dto.getChatId(), dto.getUserId());
        }

        Query eq = eq(criteria);

        return !group
                ? ApiResult.success().setData(createVo(this.find(eq)))
                : ApiResult.success().setData(createGroupVo(this.getMongoTemplate().find(eq, GroupMessageInfo.class)));
    }

    /** aroundMessage 的默认 size：centerSeq 不合法或未传 size 时，两侧各取 20 条。 */
    private static final int AROUND_DEFAULT_SIZE = 20;

    /** aroundMessage 单侧 size 上限，防止一次拉取过多。 */
    private static final int AROUND_MAX_SIZE = 50;

    /**
     * 以 centerSeq 为中心取上下文：中心消息本身 + 前 size 条 + 后 size 条，按 seq 升序返回。
     * 用于引用消息点击跳转——调用方只知道一个目标 seq。
     *
     * <p>与 {@link #completeMessage} 的「补洞」（已知两端 from/to 取中间）不同，这里只有一个中心点。
     * 群聊同样叠加 {@link #applyBlackoutGaps} 空档过滤，语义与 pull/complete 一致。
     */
    public ApiResult aroundMessage(LinkAroundMessageDto dto) {
        if (!stringValidator(dto.getChatId()) || dto.getCenterSeq() <= 0)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        boolean group = dto.getSessionType() != 1;
        if (group && !stringValidator(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        int size = dto.getSize() <= 0 ? AROUND_DEFAULT_SIZE : Math.min(dto.getSize(), AROUND_MAX_SIZE);

        // 前半：seq < center，倒序取 size 条（离中心最近的 size 条）
        Query beforeQuery = eq(aroundCriteria(dto, group).and(col(AbstractMessage::getSeq)).lt(dto.getCenterSeq()))
                .with(Sort.by(Sort.Direction.DESC, col(AbstractMessage::getSeq)))
                .limit(size);
        // 后半：seq >= center，正序取 size+1 条（含中心本身 + 后 size 条）
        Query afterQuery = eq(aroundCriteria(dto, group).and(col(AbstractMessage::getSeq)).gte(dto.getCenterSeq()))
                .with(Sort.by(Sort.Direction.ASC, col(AbstractMessage::getSeq)))
                .limit(size + 1);

        List<LinkMessageInfoVo> result = new ArrayList<>();
        if (!group) {
            List<DefaultMessageInfo> before = this.find(beforeQuery);
            Collections.reverse(before); // 倒序查出的前半翻回升序
            result.addAll(createVo(before));
            result.addAll(createVo(this.find(afterQuery)));
        } else {
            List<GroupMessageInfo> before = this.getMongoTemplate().find(beforeQuery, GroupMessageInfo.class);
            Collections.reverse(before);
            result.addAll(createGroupVo(before));
            result.addAll(createGroupVo(this.getMongoTemplate().find(afterQuery, GroupMessageInfo.class)));
        }
        return ApiResult.success().setData(result);
    }

    /** aroundMessage 的基础条件：chatId 命中 + 群聊叠加空档过滤。每次调用新建，避免 Criteria 复用污染。 */
    private Criteria aroundCriteria(LinkAroundMessageDto dto, boolean group) {
        Criteria criteria = where(col(AbstractMessage::getChatId)).is(dto.getChatId());
        if (group) {
            applyBlackoutGaps(criteria, dto.getChatId(), dto.getUserId());
        }
        return criteria;
    }

    /**
     * 把拉取者「被踢期间」的不可见空档以 $nor 叠加到查询条件上：seq 落在任意一段 blackout gap 里的消息被排除。
     * 仅「被移除过又拉回」的成员有 gaps，普通成员（gaps 为空）此方法不加任何条件，查询与原来一致。
     *
     * <p>gap.to == null 表示当前仍被踢（开口），按 seq >= from 全部排除（理论上被踢成员不会走到拉取，
     * 防御性兜底）；正常封口的 gap 按闭区间 [from, to] 排除。
     */
    private void applyBlackoutGaps(Criteria criteria, String chatId, String userId) {
        if (!ObjectId.isValid(userId))
            return;

        Query memberQuery = eq(
                where(colOf(ChatSessionMember::getChatId)).is(chatId)
                        .and(colOf(ChatSessionMember::getOwnerId)).is(new ObjectId(userId))
        );
        memberQuery.fields().include(colOf(ChatSessionMember::getBlackoutGaps));
        ChatSessionMember member = this.getMongoTemplate().findOne(memberQuery, ChatSessionMember.class);
        if (member == null || member.getBlackoutGaps() == null || member.getBlackoutGaps().isEmpty())
            return;

        List<Criteria> holes = new ArrayList<>();
        for (ChatSessionMember.Gap gap : member.getBlackoutGaps()) {
            Criteria hole = where(col(AbstractMessage::getSeq)).gte(gap.getFrom());
            if (gap.getTo() != null) {
                hole.lte(gap.getTo());
            }
            holes.add(hole);
        }
        criteria.norOperator(holes.toArray(new Criteria[0]));
    }
}
