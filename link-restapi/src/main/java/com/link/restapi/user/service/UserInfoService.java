package com.link.restapi.user.service;

import com.link.common.util.id.LinkUserNoGenerator;
import com.link.im.util.MD5Util;
import com.link.im.enums.user.UserAuthCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.user.UserInfo;
import com.link.im.util.ApiResult;
import com.link.common.util.TokenUtil;
import com.link.restapi.user.model.dto.LinkUserAuthDTO;
import com.link.restapi.user.model.dto.LinkUserRegisterDTO;
import com.link.restapi.user.model.vo.LinkUserInfoVO;


import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Slf4j
@Component
public class UserInfoService extends BasePlatFormMongoService<UserInfo> {


    public ApiResult userAuth(LinkUserAuthDTO dto) {
        UserInfo user = this.findOne(eq(UserInfo::getAccount, dto.getAccount()));
        if (user == null)
            return ApiResult.error(UserAuthCode.USER_DOES_NOT_EXIST);
        if (!MD5Util.verify(dto.getPassword(),user.getPassword()))
            return ApiResult.error(UserAuthCode.USER_PASSWORD_ERROR);

        user.setLoginTime(System.currentTimeMillis());
        return ApiResult.success(UserAuthCode.LOGIN_SUCCESS).setData(LinkUserInfoVO.from(user)).setToken(createToken(user,dto.getPlatform()));
    }

    public ApiResult userRegister(LinkUserRegisterDTO dto) {
        UserInfo user = this.findOne(new Query(Criteria.where(col(UserInfo::getAccount)).is(dto.getAccount())));
        if (user != null)
            return ApiResult.error(UserAuthCode.USER_EXIST);
        if (!dto.getPassword().equals(dto.getRePassword()))
            return ApiResult.error(UserAuthCode.PASSWORD_NOT_MATCH);
        UserInfo linkUser = dto.toUserInfo().setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next));
        this.insert(linkUser);
        return ApiResult.success(UserAuthCode.REGISTER_SUCCESS);
    }

    private String createToken(UserInfo user, int platform) {
        HashMap<String, String> tokenMap = new HashMap<>();
        tokenMap.put("userId",user.getId().toHexString());
        tokenMap.put("account",user.getAccount());
        tokenMap.put("platform",String.valueOf(platform));
        return TokenUtil.getToken(tokenMap);
    }

    public ApiResult searchUser(String userNo) {
        UserInfo user = this.getMongoTemplate().findOne(
                new Query(Criteria.where(col(UserInfo::getUserNo)).is(userNo)),
                UserInfo.class);
        if (user == null)
            return ApiResult.error(UserAuthCode.USER_DOES_NOT_EXIST);
        return ApiResult.success().setData(LinkUserInfoVO.from(user));
    }
}
