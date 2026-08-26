package com.link.restapi.module.ai.service;

import com.link.im.entity.user.UserInfo;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.restapi.module.ai.model.dto.LinkAIQueryUserDTO;
import com.link.restapi.module.user.model.vo.LinkUserInfoVO;
import com.link.restapi.module.user.service.UserInfoService;
import com.link.restapi.utils.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月26日
 */
@Slf4j
@Component
public class LinkAIUserInfoService extends BasePlatFormMongoService<UserInfo> {
    public ApiResult queryUserInfo(LinkAIQueryUserDTO dto) {
        List<ObjectId> id = dto.getUserId().stream().map(v -> { return new ObjectId(v);})
                .collect(Collectors.toList());
        List<UserInfo> userInfos = this.find(eq(where(col(UserInfo::getId)).in(id)));
        List<LinkUserInfoVO> vos = userInfos.stream().map(v -> {return LinkUserInfoVO.fromVo(v);})
                .collect(Collectors.toList());
        return ApiResult.success().setData(vos);
    }
}
