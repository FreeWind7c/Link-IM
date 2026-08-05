package com.link.restapi.module.trtc.controller;

import com.alibaba.fastjson.JSONObject;
import com.link.im.util.ApiResult;
import com.link.restapi.module.trtc.service.LinkTrtcService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * TRTC 相关接口。
 *
 * <p>路径不含 {@code /api}：网关路由时会 StripPrefix=1 剥掉，
 * 前端访问的是 {@code /api/rtc/user-sig}。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月31日
 */
@Slf4j
@RequestMapping("/trtc")
@RestController
public class LinkTrtcController {

    @Autowired
    private LinkTrtcService trtcService;


    @PostMapping("/call-back")
    public ApiResult trtcCallBack(	@RequestHeader("Sign") String sign,
                                      @RequestHeader("SdkAppId") String sdkAppId,
                                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String rawBody = new String(bodyBytes, StandardCharsets.UTF_8);
        log.info("trtc call-back -> {}" , rawBody);
        response.setStatus(200);
        return ApiResult.success();
    }

    /**
     * 签发当前登录用户的 TRTC 凭证（UserSig）。
     *
     * <p><b>身份来源是网关注入的 X-User-Id 头，不是前端传参。</b>
     * 网关的 AuthGlobalFilter 校验 JWT 后写入该头，并且在写入前会主动剥离
     * 客户端自带的同名头，因此这个值是可信的。
     *
     * <p>如果改成让前端传 userId，任何人都能签出任意用户的凭证，
     * 进而以他人身份登录腾讯云 IM、发起和接听通话——等于把整个 IM 账号体系敞开。
     */
    @GetMapping("/user-sig")
    public ApiResult userSig(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return trtcService.userSig(userId);
    }
}
