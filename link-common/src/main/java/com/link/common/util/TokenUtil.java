package com.link.common.util;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Calendar;
import java.util.Map;

/**
 * JWT工具类。
 *
 * <p>置于 link-common，供 link-im（Netty 登录鉴权）、link-restapi（登录签发 token）、
 * link-gateway（网关入口统一校验）等多模块复用。签名密钥务必各端一致。
 */
public class TokenUtil {
    private static final String SING = "$DFHAJJDJSFASJKKLJSDF==DJINDSADSFOP!";

    public static String getToken(Map<String, String> map){
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DATE,7);

        // 创建jwt builder
        final JWTCreator.Builder builder = JWT.create();

        // payload
        for (String key : map.keySet()) {
            builder.withClaim(key,map.get(key));
        }

        String sign = builder.withExpiresAt(instance.getTime()).sign(Algorithm.HMAC256(SING));
        return sign;
    }


    public static DecodedJWT verify(String token){
        DecodedJWT verify = JWT.require(Algorithm.HMAC256(SING)).build().verify(token);
        return verify;
    }
}
