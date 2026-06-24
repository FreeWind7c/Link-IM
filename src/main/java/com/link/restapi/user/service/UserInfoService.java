package com.link.restapi.user.service;

import com.link.core.config.LinkCoreConfig;
import com.link.core.session.service.LinkSession;
import com.link.im.common.enums.user.UserAuthCode;
import com.link.im.common.mongo.BaseMongoService;
import com.link.im.entity.user.UserInfo;
import com.link.restapi.user.model.dto.LinkUserAuthDTO;
import com.link.restapi.user.model.dto.LinkUserRegisterDTO;
import com.link.im.util.MD5Util;
import com.link.im.util.R;
import com.link.im.util.TokenUtil;
import com.link.util.id.LinkUserNoGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Slf4j
@Component
public class UserInfoService extends BaseMongoService<UserInfo> {

    @Autowired
    private LinkCoreConfig config;


    public R userAuth(LinkUserAuthDTO dto) {
        UserInfo user = this.findOne(eq(UserInfo::getAccount, dto.getAccount()));
        if (user == null)
            return R.error(UserAuthCode.USER_DOES_NOT_EXIST);
        if (!MD5Util.verify(dto.getPassword(),user.getPassword()))
            return R.error(UserAuthCode.USER_PASSWORD_ERROR);
        LinkSession session = this.config.getSessionManager().getSession(user.getId().toHexString(), dto.getPlatform());
        if (session != null)
            return R.error(UserAuthCode.ACCOUNT_ON_ANOTHER_DEVICE);
        user.setLoginTime(System.currentTimeMillis());
        return R.ok(UserAuthCode.LOGIN_SUCCESS).setData(user.createVO()).setToken(createToken(user,dto.getPlatform()));
    }

    public R userRegister(LinkUserRegisterDTO dto) {
        UserInfo user = this.findOne(new Query(Criteria.where(col(UserInfo::getAccount)).is(dto.getAccount())));
        if (user != null)
            return R.error(UserAuthCode.USER_EXIST);
        if (!dto.getPassword().equals(dto.getRePassword()))
            return R.error(UserAuthCode.PASSWORD_NOT_MATCH);
        UserInfo linkUser = new UserInfo().create(dto).setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next));
        this.insert(linkUser);
        return R.ok(UserAuthCode.REGISTER_SUCCESS);
    }

    private String createToken(UserInfo user, int platform) {
        HashMap<String, String> tokenMap = new HashMap<>();
        tokenMap.put("userId",user.getId().toHexString());
        tokenMap.put("account",user.getAccount());
        tokenMap.put("platform",String.valueOf(platform));
        return TokenUtil.getToken(tokenMap);
    }

    public R searchUser(String userNo) {
        UserInfo user = this.getMongoTemplate().findOne(
                new Query(Criteria.where(col(UserInfo::getUserNo)).is(userNo)),
                UserInfo.class);
        if (user == null)
            return R.error(UserAuthCode.USER_DOES_NOT_EXIST);
        return R.ok().setData(user.createVO());
    }
}
