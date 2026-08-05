package com.link.restapi.module.user.service;

import com.link.common.redis.RedisKeys;
import com.link.common.util.id.LinkUserNoGenerator;
import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.util.MD5Util;
import com.link.im.enums.user.UserApiCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.user.UserInfo;
import com.link.im.util.ApiResult;
import com.link.common.util.TokenUtil;
import com.link.restapi.module.user.model.dto.LinkUserReportTokenDto;
import com.link.restapi.module.user.model.vo.LinkUserInfoVO;
import com.link.restapi.module.user.model.dto.LinkUserAuthDTO;
import com.link.restapi.module.user.model.dto.LinkUserRegisterDTO;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Slf4j
@Component
public class UserInfoService extends BasePlatFormMongoService<UserInfo> {


    @Autowired
    private RedisTemplate redisTemplate;


    public ApiResult userAuth(LinkUserAuthDTO dto) {
        UserInfo user = this.findOne(eq(UserInfo::getAccount, dto.getAccount()));
        if (user == null)
            return ApiResult.error(UserApiCode.USER_DOES_NOT_EXIST);
        if (!MD5Util.verify(dto.getPassword(),user.getPassword()))
            return ApiResult.error(UserApiCode.USER_PASSWORD_ERROR);

        user.setLoginTime(System.currentTimeMillis());
        String token = createToken(user, dto.getPlatform());
        redisTemplate.opsForHash().put(RedisKeys.USER_TOKEN+user.getId().toHexString(),String.valueOf(dto.getPlatform()),token);

        return ApiResult.success(UserApiCode.LOGIN_SUCCESS).setData(LinkUserInfoVO.from(user))
                .setToken(token);
    }

    public ApiResult userRegister(LinkUserRegisterDTO dto) {
        UserInfo user = this.findOne(new Query(Criteria.where(col(UserInfo::getAccount)).is(dto.getAccount())));
        if (user != null)
            return ApiResult.error(UserApiCode.USER_EXIST);
        if (!dto.getPassword().equals(dto.getRePassword()))
            return ApiResult.error(UserApiCode.PASSWORD_NOT_MATCH);
        UserInfo linkUser = dto.toUserInfo().setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next));
        this.insert(linkUser);
        return ApiResult.success(UserApiCode.REGISTER_SUCCESS);
    }

    private String createToken(UserInfo user, int platform) {
        HashMap<String, String> tokenMap = new HashMap<>();
        tokenMap.put("userId",user.getId().toHexString());
        tokenMap.put("account",user.getAccount());
        tokenMap.put("platform",String.valueOf(platform));
        tokenMap.put("timestamp",String.valueOf(now()));
        return TokenUtil.getToken(tokenMap);
    }

    public ApiResult searchUser(String userNo) {
        UserInfo user = this.getMongoTemplate().findOne(
                new Query(Criteria.where(col(UserInfo::getUserNo)).is(userNo)),
                UserInfo.class);
        if (user == null)
            return ApiResult.error(UserApiCode.USER_DOES_NOT_EXIST);
        return ApiResult.success().setData(LinkUserInfoVO.from(user));
    }

    public ApiResult verifySurvivalStatus(LinkUserReportTokenDto dto) {
        if (!stringValidator(dto.getUserId(),dto.getToken()) || dto.getPlatform() == null)
            return ApiResult.error(GlobalCode.PARAMETER_VALIDATOR_ERROR);

        String token = (String) redisTemplate.opsForHash().get(RedisKeys.USER_TOKEN+dto.getUserId(), String.valueOf(dto.getPlatform()));
        if (!stringValidator(token))
            return ApiResult.success().setData(false);

        return ApiResult.success().setData(dto.getToken().equals(token));
    }
}
