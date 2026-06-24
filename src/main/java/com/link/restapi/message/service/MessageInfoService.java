package com.link.restapi.message.service;

import com.link.im.common.enums.gloabl.GlobalCode;
import com.link.im.common.mongo.BaseMongoService;
import com.link.im.entity.message.AbstractMessage;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.util.R;
import com.link.restapi.message.model.dto.LinkPullMessageDto;
import com.link.restapi.message.model.vo.LinkDefaultMessageVo;
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
public class MessageInfoService extends BaseMongoService<DefaultMessageInfo>  {


    public R pullMessage(LinkPullMessageDto dto) {
        if (!stringValidator(dto.getChatId()) || !pageValidator(dto.getSkip(),dto.getLimit()))
            return R.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        Query eq = eq(
                where(col(AbstractMessage::getChatId)).is(dto.getChatId())
        );
        eq.with(Sort.by(Sort.Direction.DESC,col(AbstractMessage::getTimestamp)));
        eq.skip(dto.getSkip());
        eq.limit(dto.getLimit());

        List<DefaultMessageInfo> messages = this.find(eq);
        System.out.println("meesage:"+messages.size());
        return R.ok().setData(createVo(messages));
    }

    private List<LinkDefaultMessageVo> createVo(List<DefaultMessageInfo> messages) {
        List<LinkDefaultMessageVo> vos = messages.stream().map(item -> {
            return item.createVo();
        }).collect(Collectors.toList());
        return vos;
    }
}
