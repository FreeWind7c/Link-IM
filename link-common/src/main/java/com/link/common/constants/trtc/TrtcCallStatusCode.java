package com.link.common.constants.trtc;


import java.io.PipedOutputStream;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月16日
 */

public class TrtcCallStatusCode {

    // 未接通
    public static final int NO_ANSWER = 0;

    // 主动取消
    public static int CANCEL = 1;

    // 被动拒绝
    public static int REJECT = 2;

    // 挂断，通话结束
    public static int CALL_ENDED = 3;

}
