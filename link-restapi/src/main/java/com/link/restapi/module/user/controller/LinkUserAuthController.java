package com.link.restapi.module.user.controller;

import com.link.base.entity.user.UserInfo;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.redis.RedisKeys;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.restapi.module.user.model.dto.LinkUserAuthTokenDTO;
import com.link.restapi.module.user.service.UserInfoService;
import com.link.restapi.utils.ApiResult;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年09月04日
 */

@RestController
@RequestMapping("/auth")
public class LinkUserAuthController {


    @Autowired
    private BasePlatFormRedisService redisService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @PostMapping("/refresh-token")
    public ApiResult refreshToken(@RequestBody LinkUserAuthTokenDTO dto){
        if (StringUtils.isEmpty(dto.getUserId()) || StringUtils.isEmpty(dto.getRefreshToken()) || (dto.getPlatform()<1 || dto.getPlatform()>3))
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String token = (String) redisService.getObject(RedisKeys.USER_REFRESH_TOKEN + dto.getUserId() + ":" + dto.getPlatform());
        if (StringUtils.isEmpty(token))
            return ApiResult.success(GlobalCode.TOKEN_EXPIRED);

        UserInfo user = this.mongoTemplate.findById(new ObjectId(dto.getUserId()), UserInfo.class);
        if (user == null)
            return ApiResult.success(GlobalCode.TOKEN_EXPIRED);

        String accessToken = userInfoService.createToken(user, dto.getPlatform(), 15  * 1000);
        redisService.expireKey(RedisKeys.USER_REFRESH_TOKEN + dto.getUserId() + ":" + dto.getPlatform(),7L, TimeUnit.DAYS);

        return ApiResult.success().setData(accessToken);
    }


}
