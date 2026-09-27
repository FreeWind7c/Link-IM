package com.link.restapi.module.message.service;

import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.message.GroupMessageInfo;
import com.link.base.entity.chat.ChatSessionMember;
import com.link.base.entity.message.type.MessageType;
import com.link.base.entity.redpack.RedPacket;
import com.link.base.entity.rtc.TrtcCallInfo;
import com.link.base.provider.MessageData;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.restapi.utils.ApiResult;
import com.link.restapi.module.message.model.dto.LinkAroundMessageDto;
import com.link.restapi.module.message.model.dto.LinkCompleteMessageDto;
import com.link.restapi.module.message.model.dto.LinkPullMessageDto;
import com.link.restapi.module.message.model.vo.LinkMessageInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
@Slf4j
@Component
public class LinkMessageInfoService extends BasePlatFormMongoService<DefaultMessageInfo> {


    @Autowired
    private LinkMessageDataService messageDataService;

    public ApiResult pullMessage(LinkPullMessageDto dto) {
        if (!stringValidator(dto.getChatId()) || !pageValidator(dto.getSkip(),dto.getLimit()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        boolean group = dto.getSessionType() != 1;
        // 群聊必须带 userId，否则无法按其 blackoutGaps 过滤被踢期间的消息
        if (group && !stringValidator(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Criteria criteria = where(col(BaseMessage::getChatId)).is(dto.getChatId());
        if (group) {
            applyBlackoutGaps(criteria, dto.getChatId(), dto.getUserId());
        }

        Query eq = eq(criteria);
        eq.with(Sort.by(Sort.Direction.DESC,col(BaseMessage::getSeq)));
        eq.skip(dto.getSkip());
        eq.limit(dto.getLimit());

        return !group
                ? ApiResult.success().setData(createVo(this.find(eq)))
                : ApiResult.success().setData(createGroupVo(this.getMongoTemplate().find(eq, GroupMessageInfo.class)));
    }

    public List<LinkMessageInfoVo> createVo(List<DefaultMessageInfo> messages) {
        List<DefaultMessageInfo> rtcMessage = messages.stream().filter(item -> { return item.getType() == MessageType.RTC_CALL_MESSAGE.getType();
        }).collect(Collectors.toList());
        List<DefaultMessageInfo> redPacketMessage = messages.stream().filter(item -> {
            return item.getType() == MessageType.RED_PACK_MESSAGE.getType();
        }).collect(Collectors.toList());

        Map<Integer,Map<ObjectId,? extends MessageData>> map = new HashMap<>();

        if (!rtcMessage.isEmpty())
        {
            Map<ObjectId, TrtcCallInfo> dataMap = messageDataService.pullBaseData(rtcMessage, TrtcCallInfo.class);
            map.put(MessageType.RTC_CALL_MESSAGE.getType(),dataMap);
        }
        if (!redPacketMessage.isEmpty())
        {
            Map<ObjectId, RedPacket> dataMap = messageDataService.pullBaseData(redPacketMessage, RedPacket.class);
            map.put(MessageType.RED_PACK_MESSAGE.getType(),dataMap);
        }

        List<LinkMessageInfoVo> vos = messages.stream().map(item -> {
            LinkMessageInfoVo vo = new LinkMessageInfoVo();
            BeanUtils.copyProperties(item, vo);
            vo.setId(item.getId().toHexString());
            vo.setSndId(item.getSndId().toHexString());
            vo.setRcvId(item.getRcvId().toHexString());
            messageDataService.createData(item,vo,map);
            return vo;
        }).collect(Collectors.toList());
        return vos;
    }

    private Map<ObjectId, RedPacket>  pullRedPacketData(List<DefaultMessageInfo> redPacketMessage) {
        List<ObjectId> msgIds = redPacketMessage.stream().map(item -> { return item.getId();
        }).collect(Collectors.toList());
        List<RedPacket> redPackets = this.getMongoTemplate().find(eq(
                where(col(RedPacket::getMessageId)).in(msgIds)
        ), RedPacket.class);
        if (redPackets != null && !redPackets.isEmpty()){
            return redPackets.stream().collect(Collectors.toMap(RedPacket::getMessageId, Function.identity()));
        }
        return null;
    }


    private Map<ObjectId, TrtcCallInfo> pullCallData(List<DefaultMessageInfo> rtcMessage) {
        List<ObjectId> msgIds = rtcMessage.stream().map(item -> { return item.getId();
        }).collect(Collectors.toList());
        List<TrtcCallInfo> callInfos = this.getMongoTemplate().find(eq(
                where(col(TrtcCallInfo::getMessageId)).in(msgIds)
        ), TrtcCallInfo.class);
        if (callInfos != null && !callInfos.isEmpty()){
            return callInfos.stream().collect(Collectors.toMap(TrtcCallInfo::getMessageId, Function.identity()));
        }
        return null;
    }

    public List<LinkMessageInfoVo> createGroupVo(List<GroupMessageInfo> messages) {
        List<GroupMessageInfo> redPacketMessage = messages.stream().filter(item -> {
            return item.getType() == MessageType.RED_PACK_MESSAGE.getType();
        }).collect(Collectors.toList());
        Map<Integer,Map<ObjectId,? extends MessageData>> map = new HashMap<>();

        if (!redPacketMessage.isEmpty())
        {
            Map<ObjectId, RedPacket> dataMap = messageDataService.pullBaseData(redPacketMessage, RedPacket.class);
            map.put(MessageType.RED_PACK_MESSAGE.getType(),dataMap);
        }

        List<LinkMessageInfoVo> vos = messages.stream().map(item -> {
            LinkMessageInfoVo vo = new LinkMessageInfoVo();
            BeanUtils.copyProperties(item, vo);
            vo.setId(item.getId().toHexString());
            vo.setSndId(item.getSndId().toHexString());
            vo.setRcvId(item.getRcvId().toHexString());
            messageDataService.createData(item,vo,map);
            return vo;
        }).collect(Collectors.toList());
        return vos;
    }

    public ApiResult completeMessage(LinkCompleteMessageDto dto) {
        boolean group = dto.getSessionType() != 1;
        if (group && !stringValidator(dto.getUserId()))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Criteria criteria = where(col(BaseMessage::getChatId)).is(dto.getChatId())
                .and(col(BaseMessage::getSeq)).gt(dto.getFrom()).lt(dto.getTo());
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
        Query beforeQuery = eq(aroundCriteria(dto, group).and(col(BaseMessage::getSeq)).lt(dto.getCenterSeq()))
                .with(Sort.by(Sort.Direction.DESC, col(BaseMessage::getSeq)))
                .limit(size);
        // 后半：seq >= center，正序取 size+1 条（含中心本身 + 后 size 条）
        Query afterQuery = eq(aroundCriteria(dto, group).and(col(BaseMessage::getSeq)).gte(dto.getCenterSeq()))
                .with(Sort.by(Sort.Direction.ASC, col(BaseMessage::getSeq)))
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
        Criteria criteria = where(col(BaseMessage::getChatId)).is(dto.getChatId());
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
            Criteria hole = where(col(BaseMessage::getSeq)).gte(gap.getFrom());
            if (gap.getTo() != null) {
                hole.lte(gap.getTo());
            }
            holes.add(hole);
        }
        criteria.norOperator(holes.toArray(new Criteria[0]));
    }
}
