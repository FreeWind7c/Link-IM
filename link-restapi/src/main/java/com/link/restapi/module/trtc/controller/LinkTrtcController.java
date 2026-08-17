package com.link.restapi.module.trtc.controller;

import com.alibaba.fastjson.JSONObject;
import com.link.core.util.seq.MessageSeqAllocator;
import com.link.im.constants.publisher.PublisherRouterKeys;
import com.link.im.constants.trtc.TrtcCallStatusCode;
import com.link.im.entity.base.BaseMessage;
import com.link.im.entity.data.NoticeData;
import com.link.im.entity.data.notice.TrtcCallNoticeData;
import com.link.im.entity.message.DefaultMessageInfo;
import com.link.im.entity.message.GroupMessageInfo;
import com.link.im.entity.message.type.MessageType;
import com.link.im.entity.rtc.TrtcCallInfo;
import com.link.im.util.ApiResult;
import com.link.restapi.module.trtc.model.dto.LinkTrtcRoomIdDTO;
import com.link.restapi.module.trtc.service.LinkTrtcService;
import com.link.restapi.push.RemotePushPublisher;
import com.link.restapi.utils.LinkTrtcSignUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;

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

    @Autowired
    private LinkTrtcSignUtil trtcSignUtil;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RemotePushPublisher pushPublisher;

    @Autowired
    private MessageSeqAllocator allocator;


    @PostMapping("/start-call")
    public ApiResult startCall(@RequestBody LinkTrtcRoomIdDTO dto){
        return trtcService.startCall(dto);
    }




    @PostMapping("/call-back")
    public ApiResult trtcCallBack(	@RequestHeader("Sign") String sign,
                                      @RequestHeader("SdkAppId") String sdkAppId,
                                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String rawBody = new String(bodyBytes, StandardCharsets.UTF_8);
        log.info("trtc call-back -> {}" , rawBody);
        response.setStatus(200);
        JSONObject data = JSONObject.parseObject(rawBody);
        JSONObject eventInfo = data.getJSONObject("EventInfo");
        switch (data.getIntValue("EventType"))
        {
            // 解散房间
            case 102:
                trtcService.dissolveRoom(eventInfo);
                break;
            // 加入房间
            case 103:
                trtcService.joinRoom(eventInfo);
                break;
            // 离开房间
            case 104:
                trtcService.leaveRoom(eventInfo);
                break;
            default:
                log.error("未知事件!");
        }
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
        return trtcSignUtil.userSig(userId);
    }


}
