package com.link.restapi.module.user.service;

import com.link.common.redis.RedisKeys;
import com.link.common.util.id.ChatIdGenerator;
import com.link.common.util.id.LinkUserNoGenerator;
import com.link.common.constants.group.LinkNoticeTemplate;
import com.link.common.constants.friend.LinkFriendSource;
import com.link.common.constants.session.ChatSessionCategoryKeys;
import com.link.common.constants.user.UserCategoryKeys;
import com.link.im.entity.chat.ChatSession;
import com.link.im.entity.chat.ChatSessionMember;
import com.link.im.entity.data.TextData;
import com.link.im.entity.friend.FriendInfo;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.im.factory.LinkBaseDataFactory;
import com.link.im.factory.LinkMessageFactory;
import com.link.im.repository.ChatSessionRepository;
import com.link.im.util.MD5Util;
import com.link.restapi.enums.user.UserApiCode;
import com.link.im.mongo.BasePlatFormMongoService;
import com.link.im.entity.user.UserInfo;
import com.link.restapi.utils.ApiResult;
import com.link.common.util.TokenUtil;
import com.link.im.vo.DefaultMessageVO;
import com.link.restapi.module.user.model.dto.LinkUserReportTokenDto;
import com.link.restapi.module.user.model.vo.LinkUserInfoVO;
import com.link.restapi.module.user.model.dto.LinkUserAuthDTO;
import com.link.restapi.module.user.model.dto.LinkUserRegisterDTO;


import com.link.restapi.push.RemotePushPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class UserInfoService extends BasePlatFormMongoService<UserInfo> {


    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private LinkBaseDataFactory dataFactory;

    @Autowired
    private LinkMessageFactory messageFactory;

    @Autowired
    public RemotePushPublisher pushPublisher;

    @Autowired
    private ChatSessionRepository sessionRepository;


    public ApiResult userAuth(LinkUserAuthDTO dto) {
        UserInfo user = this.findOne(eq(UserInfo::getAccount, dto.getAccount()));
        if (user == null)
            return ApiResult.error(UserApiCode.USER_DOES_NOT_EXIST);
        if (!MD5Util.verify(dto.getPassword(),user.getPassword()))
            return ApiResult.error(UserApiCode.USER_PASSWORD_ERROR);

        user.setLoginTime(System.currentTimeMillis());
        String token = createToken(user, dto.getPlatform());
        redisTemplate.opsForHash().put(RedisKeys.USER_TOKEN+user.getId().toHexString(),String.valueOf(dto.getPlatform()),token);

        return ApiResult.success(UserApiCode.LOGIN_SUCCESS).setData(LinkUserInfoVO.fromVo(user))
                .setToken(token);
    }

    @Transactional(rollbackFor = Exception.class)
    public ApiResult userRegister(LinkUserRegisterDTO dto) {
        UserInfo user = this.findOne(new Query(Criteria.where(col(UserInfo::getAccount)).is(dto.getAccount())));
        if (user != null)
            return ApiResult.error(UserApiCode.USER_EXIST);
        if (!dto.getPassword().equals(dto.getRePassword()))
            return ApiResult.error(UserApiCode.PASSWORD_NOT_MATCH);
        UserInfo linkUser = dto.toUserInfo().setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next)).setCategory(UserCategoryKeys.USER
        );
        this.insert(linkUser);
        // 创建机器人好友与会话
        UserInfo boot = this.findOne(eq(where(col(UserInfo::getCategory)).is(UserCategoryKeys.BOOT)));
        createFriend(linkUser,boot);
        String chatId = createChat(linkUser, boot);
        createMessage(chatId,linkUser,boot);
        return ApiResult.success(UserApiCode.REGISTER_SUCCESS);
    }

    private void createMessage(String chatId, UserInfo linkUser, UserInfo boot) {
        TextData textData = this.dataFactory.createTextData(LinkNoticeTemplate.BOT_WELCOME_MESSAGE);
        DefaultMessageInfo message = (DefaultMessageInfo) messageFactory.create(chatId, boot.getId(), linkUser.getId(), textData.getMessageType(), textData, DefaultMessageInfo.class);
        DefaultMessageVO vo = message.toVo();
        this.pushPublisher.messageStorage(vo,vo.getMessageType());
        this.sessionRepository.updateSession(message);
    }

    private String createChat(UserInfo linkUser, UserInfo boot) {
        ChatSession session = new ChatSession().createSingle(ChatIdGenerator.nextId(linkUser.getId().toHexString(), boot.getId().toHexString()), ChatSessionCategoryKeys.BOOT);
        ChatSessionMember s1 = new ChatSessionMember().createSingle(linkUser.getId().toHexString(), boot.getId().toHexString(), session.getChatId());
        ChatSessionMember s2 = new ChatSessionMember().createSingle(boot.getId().toHexString(), linkUser.getId().toHexString(),  session.getChatId());
        this.getMongoTemplate().insert(session);
        this.getMongoTemplate().insert(List.of(s1,s2),ChatSessionMember.class);
        return session.getChatId();
    }

    private void createFriend(UserInfo linkUser, UserInfo boot) {
        FriendInfo f1 = new FriendInfo().create(linkUser.getId().toHexString(), boot.getId().toHexString(), LinkFriendSource.SYS);
        FriendInfo f2 = new FriendInfo().create( boot.getId().toHexString(), linkUser.getId().toHexString(), LinkFriendSource.SYS);
        this.getMongoTemplate().insert(List.of(f1, f2),FriendInfo.class);
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
        return ApiResult.success().setData(LinkUserInfoVO.fromVo(user));
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
