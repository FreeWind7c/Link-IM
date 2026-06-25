package com.link.restapi.user.service;

import com.link.common.util.id.LinkUserNoGenerator;
import com.link.im.util.MD5Util;
import com.link.im.enums.user.UserAuthCode;
import com.link.im.mongo.BaseMongoService;
import com.link.im.entity.user.UserInfo;
import com.link.im.util.R;
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
public class UserInfoService extends BaseMongoService<UserInfo> {


    public R userAuth(LinkUserAuthDTO dto) {
        UserInfo user = this.findOne(eq(UserInfo::getAccount, dto.getAccount()));
        if (user == null)
            return R.error(UserAuthCode.USER_DOES_NOT_EXIST);
        if (!MD5Util.verify(dto.getPassword(),user.getPassword()))
            return R.error(UserAuthCode.USER_PASSWORD_ERROR);
        // 异地登录策略统一在连接层处理：DefaultChannelSessionManager.addSession 对「同 userId+同 platform」
        // 踢旧——后登录的设备顶掉先登录的同端设备。此处 HTTP 登录是无状态的，只验密码发令牌，
        // 不再查在线 session（restapi 进程不持长连接、sessionMap 恒空，查了也永远失效，
        // 且与连接层的「踢旧」策略相反，会造成代码意图与真实行为不一致）。
        user.setLoginTime(System.currentTimeMillis());
        return R.ok(UserAuthCode.LOGIN_SUCCESS).setData(LinkUserInfoVO.from(user)).setToken(createToken(user,dto.getPlatform()));
    }

    public R userRegister(LinkUserRegisterDTO dto) {
        UserInfo user = this.findOne(new Query(Criteria.where(col(UserInfo::getAccount)).is(dto.getAccount())));
        if (user != null)
            return R.error(UserAuthCode.USER_EXIST);
        if (!dto.getPassword().equals(dto.getRePassword()))
            return R.error(UserAuthCode.PASSWORD_NOT_MATCH);
        UserInfo linkUser = dto.toUserInfo().setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next));
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
        return R.ok().setData(LinkUserInfoVO.from(user));
    }
}
