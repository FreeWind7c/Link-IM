package com.link.restapi.module.user.service;

import com.google.gson.Gson;
import com.link.base.redis.BasePlatFormRedisService;
import com.link.common.redis.RedisKeys;
import com.link.common.util.id.LinkUserNoGenerator;
import com.link.common.constants.group.LinkNoticeTemplate;
import com.link.common.constants.user.UserCategoryKeys;
import com.link.base.entity.data.NoticeData;
import com.link.base.entity.data.TextData;
import com.link.base.entity.friend.FriendInfo;
import com.link.base.entity.message.DefaultMessageInfo;
import com.link.restapi.enums.gloabl.GlobalCode;
import com.link.base.facotry.LinkBaseDataFactory;
import com.link.base.facotry.LinkMessageFactory;
import com.link.base.repository.ChatSessionRepository;
import com.link.common.util.MD5Util;
import com.link.restapi.enums.user.UserApiCode;
import com.link.base.mongo.BasePlatFormMongoService;
import com.link.base.entity.user.UserInfo;
import com.link.restapi.utils.ApiResult;
import com.link.common.util.TokenUtil;
import com.link.base.vo.DefaultMessageVO;
import com.link.restapi.module.user.model.dto.LinkUserReportTokenDto;
import com.link.restapi.module.user.model.vo.LinkUserInfoVO;
import com.link.restapi.module.user.model.dto.LinkUserAuthDTO;
import com.link.restapi.module.user.model.dto.LinkUserRegisterDTO;


import com.link.restapi.push.RemotePushPublisher;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class UserInfoService extends BasePlatFormMongoService<UserInfo> {


    @Autowired
    private BasePlatFormRedisService redisService;

    @Autowired
    private LinkBaseDataFactory dataFactory;

    @Autowired
    private LinkMessageFactory messageFactory;

    @Autowired
    public RemotePushPublisher pushPublisher;

    @Autowired
    private ChatSessionRepository sessionRepository;

    @Autowired
    private UserTxExecutorService tx;


    public ApiResult userLogin(LinkUserAuthDTO dto) {
        UserInfo user = this.findOne(eq(UserInfo::getAccount, dto.getAccount()));
        if (user == null)
            return ApiResult.error(UserApiCode.USER_DOES_NOT_EXIST);
        if (!MD5Util.verify(dto.getPassword(),user.getPassword()))
            return ApiResult.error(UserApiCode.USER_PASSWORD_ERROR);

        user.setLoginTime(System.currentTimeMillis());
        String accessToken = createToken(user, dto.getPlatform(),1000 * 15);
        String refreshToken = createToken(user, dto.getPlatform(),1000 * 60 * 60 * 72);
        redisService.setObject(RedisKeys.USER_ACCESS_TOKEN+user.getId().toHexString()+":"+dto.getPlatform(),accessToken,15L,TimeUnit.MINUTES);
        redisService.setObject(RedisKeys.USER_REFRESH_TOKEN+user.getId().toHexString()+":"+dto.getPlatform(),refreshToken,3L,TimeUnit.DAYS);
        return ApiResult.success(UserApiCode.LOGIN_SUCCESS)
                    .setData(LinkUserInfoVO.fromVo(user))
                    .setToken("accessToken",accessToken)
                    .setToken("refreshToken",refreshToken);
    }

    public ApiResult userRegister(LinkUserRegisterDTO dto) {
        UserInfo user = this.getMongoTemplate().findOne(new Query(Criteria.where(col(UserInfo::getAccount)).is(dto.getAccount())),UserInfo.class);
        if (user != null)
            return ApiResult.error(UserApiCode.USER_EXIST);
        if (!dto.getPassword().equals(dto.getRePassword()))
            return ApiResult.error(UserApiCode.PASSWORD_NOT_MATCH);
        UserInfo linkUser = dto.toUserInfo().setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next)).setCategory(UserCategoryKeys.USER
        );
        UserTxExecutorService.RegisterContext context = tx.registerTx(linkUser);
        createMessage(context.getChatId(), linkUser,context.getBot());
        return ApiResult.success(UserApiCode.REGISTER_SUCCESS);
    }



    private void createMessage(String chatId, UserInfo linkUser, UserInfo boot) {
        TextData textData = this.dataFactory.createTextData(LinkNoticeTemplate.BOT_WELCOME_MESSAGE);
        DefaultMessageInfo message = (DefaultMessageInfo) messageFactory.create(chatId, boot.getId(), linkUser.getId(), textData.getMessageType(), textData, DefaultMessageInfo.class);
        DefaultMessageVO vo = message.toVo();
        this.pushPublisher.messageStorage(vo,vo.getMessageType());
        this.sessionRepository.updateSession(message);
    }
    public String createToken(UserInfo user, int platform, long expireTime) {
        HashMap<String, String> tokenMap = new HashMap<>();
        tokenMap.put("userId",user.getId().toHexString());
        tokenMap.put("account",user.getAccount());
        tokenMap.put("platform",String.valueOf(platform));
        tokenMap.put("timestamp",String.valueOf(now()));
        tokenMap.put("expireTime", String.valueOf(System.currentTimeMillis() + expireTime));
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

        System.out.println("dto:" + new Gson().toJson(dto));

        // 检查 refreshToken 来判断用户是否真正在线（登录会话是否还存在）
        String token = (String) redisService.getObject(RedisKeys.USER_REFRESH_TOKEN+dto.getUserId()+":"+dto.getPlatform());


        System.out.println("token:" + token);
        if (!stringValidator(token))
            return ApiResult.success().setData(false);

        return ApiResult.success().setData(dto.getToken().equals(token));
    }

    /** 初始化账号列表，admin 放首位：后续普通用户都要和它建立好友关系。 */
    private static final List<String> INIT_ACCOUNTS =
            List.of("admin", "test", "user", "main", "dev", "lark", "pork", "less", "tom", "jin");

    /** 初始化账号统一密码，入库前 MD5。 */
    private static final String INIT_PASSWORD = "123123";

    /**
     * 初始化基础数据：
     * <ol>
     *   <li>先建机器人——普通用户注册要给它绑好友和会话，顺序不能反；</li>
     *   <li>再按 {@link #INIT_ACCOUNTS} 建普通用户，每个都走注册那套：绑机器人好友 + 会话 + 欢迎消息推送；</li>
     *   <li>最后让除 admin 以外的每个普通用户和 admin 建立好友关系，普通用户之间不互加。</li>
     * </ol>
     * 可重复调用：已存在的用户和好友关系会跳过，不会重复写入。
     */
    public ApiResult initData() {
        createBot();
        UserInfo bot = this.findOne(eq(where(col(UserInfo::getCategory)).is(UserCategoryKeys.BOT)));
        if (bot == null)
            return ApiResult.error("机器人创建失败，初始化中止");

        Map<String, UserInfo> users = new LinkedHashMap<>();
        List<String> createdUsers = new ArrayList<>();
        for (String account : INIT_ACCOUNTS) {
            UserInfo existed = this.findOne(eq(UserInfo::getAccount, account));
            if (existed != null) {
                users.put(account, existed);
                continue;
            }
            users.put(account, createInitUser(account, bot));
            createdUsers.add(account);
        }

        UserInfo admin = users.get(INIT_ACCOUNTS.get(0));
        List<String> boundFriends = new ArrayList<>();
        for (String account : INIT_ACCOUNTS) {
            if (account.equals(admin.getAccount()))
                continue;
            if (bindAdminFriend(users.get(account), admin))
                boundFriends.add(account);
        }

        log.info("初始化完成 新建用户={} 新建好友关系={}", createdUsers, boundFriends);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("botAccount", bot.getAccount());
        data.put("createdUsers", createdUsers);
        data.put("boundAdminFriends", boundFriends);
        return ApiResult.success().setData(data);
    }

    /** 按注册流程建一个初始化用户：入库 + 绑定机器人好友会话 + 推送机器人欢迎消息。 */
    private UserInfo createInitUser(String account, UserInfo bot) {
        UserInfo user = new UserInfo()
                .setAccount(account)
                .setPassword(MD5Util.encrypt(INIT_PASSWORD))
                .setNickname("用户" + account)
                .setCategory(UserCategoryKeys.USER)
                .setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next))
                .setRegisterTime(now());

        UserTxExecutorService.RegisterContext context = tx.registerTx(user);
        createMessage(context.getChatId(), user, context.getBot());
        return user;
    }

    /**
     * 让一个普通用户和 admin 建立好友关系与会话，并落一条好友欢迎消息。
     *
     * @return true 表示本次新建，false 表示关系已存在被跳过
     */
    private boolean bindAdminFriend(UserInfo user, UserInfo admin) {
        boolean existed = this.getMongoTemplate().exists(
                eq(where(col(FriendInfo::getUserId)).is(user.getId())
                        .and(col(FriendInfo::getFriendId)).is(admin.getId())),
                FriendInfo.class);
        if (existed)
            return false;

        String chatId = tx.bindFriendTx(user, admin);
        createFriendMessage(chatId, user, admin);
        return true;
    }

    /** 好友关系建立后的系统通知消息，与好友申请通过时保持一致。 */
    private void createFriendMessage(String chatId, UserInfo user, UserInfo friend) {
        NoticeData noticeData = this.dataFactory.createDefaultNoticeData(chatId, LinkNoticeTemplate.FRIEND_WELCOME_MESSAGE);
        DefaultMessageInfo message = (DefaultMessageInfo) this.messageFactory.create(
                chatId, user.getId(), friend.getId(),
                noticeData.getMessageType(), noticeData, DefaultMessageInfo.class);
        DefaultMessageVO vo = message.toVo();
        this.pushPublisher.messageStorage(vo, vo.getMessageType());
        this.sessionRepository.updateSession(message);
    }

    public ApiResult createBot() {
        boolean exists = this.exists(eq(where(col(UserInfo::getCategory)).is(UserCategoryKeys.BOT)));
        if (exists)
            return ApiResult.success();
        UserInfo userInfo = new UserInfo();
        userInfo.setId(new ObjectId())
                .setCategory(UserCategoryKeys.BOT)
                .setUserNo(nextUnique(UserInfo::getUserNo, LinkUserNoGenerator::next))
                .setAvatar("https://gbres.dfcfw.com/Files/iimage/20240319/67778C224AB7D8EA534BA4F9E91B619F_w1080h1080.png")
                .setAccount("bot")
                .setPassword(MD5Util.encrypt("123123"))
                .setNickname("Link Bot")
                .setRegisterTime(now());
        this.insert(userInfo);
        return ApiResult.success();
    }
}
