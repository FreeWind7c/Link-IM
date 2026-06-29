package com.link.restapi.message.service;

import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.util.ApiResult;
import com.link.restapi.message.model.dto.LinkCompleteMessageDto;
import com.link.restapi.message.model.dto.LinkPullMessageDto;
import com.link.restapi.message.model.vo.LinkMessageInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

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

        Query eq = eq(
                where(col(AbstractMessage::getChatId)).is(dto.getChatId())
        );
        eq.with(Sort.by(Sort.Direction.DESC,col(AbstractMessage::getTimestamp)));
        eq.skip(dto.getSkip());
        eq.limit(dto.getLimit());

        return dto.getSessionType() == 1
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
        Query eq = eq(
                where(col(AbstractMessage::getChatId)).is(dto.getChatId())
                        .and(col(AbstractMessage::getSeq)).gt(dto.getFrom()).lt(dto.getTo())
        );

        return dto.getSessionType() == 1
                ? ApiResult.success().setData(createVo(this.find(eq)))
                : ApiResult.success().setData(createGroupVo(this.getMongoTemplate().find(eq, GroupMessageInfo.class)));
    }
}
