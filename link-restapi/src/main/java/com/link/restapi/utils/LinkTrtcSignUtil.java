package com.link.restapi.utils;

import com.link.im.enums.gloabl.GlobalCode;
import com.link.im.util.ApiResult;
import com.link.restapi.module.trtc.config.TLSSigAPIv2;
import com.link.restapi.module.trtc.config.TrtcProperties;
import com.link.restapi.module.trtc.model.vo.LinkTrtcUserSigVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Slf4j
@Component
public class LinkTrtcSignUtil {
    @Autowired
    private TrtcProperties properties;

    /**
     * 官方签名器实例。SDKAppID / SecretKey 在运行期固定，
     * 缓存一份即可，不必每次签发都 new（它内部只有两个 final 字段，线程安全）。
     */
    private volatile TLSSigAPIv2 sigApi;

    /**
     * 懒加载签名器。放在这里而非构造函数，是因为 @ConfigurationProperties
     * 的绑定发生在依赖注入之后，构造期取不到配置值。
     */
    private TLSSigAPIv2 sigApi() {
        TLSSigAPIv2 api = this.sigApi;
        if (api == null) {
            synchronized (this) {
                api = this.sigApi;
                if (api == null) {
                    api = new TLSSigAPIv2(properties.getSdkAppId(), properties.getSecretKey());
                    this.sigApi = api;
                }
            }
        }
        return api;
    }

    /**
     * 为当前登录用户签发 TRTC 凭证。
     *
     * @param userId 当前登录用户 ID，<b>由网关从 JWT 解出并写入 X-User-Id 头</b>，
     *               不接受前端传参——否则等于把签发权交还给前端，
     *               任何人都能以他人身份登录腾讯云 IM。
     */
    public ApiResult userSig(String userId) {
        if (!StringUtils.hasText(userId)) {
            // 正常情况下网关已拦截无 token 请求，走到这里说明网关配置有问题
            log.warn("签发 TRTC 凭证失败：缺少登录身份，请检查网关是否注入 X-User-Id");
            return ApiResult.notLogin();
        }

        if (!StringUtils.hasText(properties.getSecretKey()) || properties.getSdkAppId() <= 0) {
            log.error("TRTC 未配置：link.trtc.sdk-app-id / link.trtc.secret-key 缺失");
            return ApiResult.error(GlobalCode.GLOBAL_ERROR);
        }

        if (!checkPermission(userId)) {
            return ApiResult.error(GlobalCode.NO_PERMISSION);
        }

        long expireSeconds = properties.getExpireSeconds();
        String userSig = sigApi().genUserSig(userId, expireSeconds);
        // 官方实现里 HMAC 异常会吞掉并返回空串，这里必须拦住，
        // 否则前端拿着空 sig 去 init() 只会得到一个语义不明的 SDK 报错
        if (!StringUtils.hasText(userSig)) {
            log.error("签发 TRTC 凭证失败：签名结果为空 -> userId={}", userId);
            return ApiResult.error(GlobalCode.GLOBAL_ERROR);
        }

        LinkTrtcUserSigVo vo = new LinkTrtcUserSigVo()
                .setSdkAppId(properties.getSdkAppId())
                .setUserId(userId)
                .setUserSig(userSig)
                .setExpireAt(System.currentTimeMillis() + expireSeconds * 1000L);

        log.info("签发 TRTC 凭证 -> userId={} 有效期={}s", userId, expireSeconds);
        return ApiResult.success().setData(vo);
    }

    /**
     * 通话权限校验。当前默认放行，按业务需要在此扩展。
     *
     * <p>这里是唯一有效的拦截点：一旦凭证发出去，用户就能直接通过腾讯云 IM
     * 发起通话，我方后端拦不住（信令不经过我们）。而且凭证在有效期内一直可用，
     * 所以「发出去之后再封禁」是无法立即生效的——只能等凭证过期。
     * 若业务对实时性要求高，应缩短 {@code expireSeconds}。
     *
     * <p>可扩展的校验项：账号是否被封禁 / 是否在禁言期 / 会员等级是否允许群通话等。
     */
    private boolean checkPermission(String userId) {
        return true;
    }
}
