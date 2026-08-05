package com.link.restapi.module.redpack.exception;

import com.link.im.util.ApiResult;

/**
 * 红包业务失败信号。
 *
 * <p>存在的唯一理由：事务方法里业务不通过时必须<b>抛</b>，不能<b>return</b>。
 * {@code @Transactional} 只在 RuntimeException 逃出方法时回滚，
 * {@code return ApiResult.error(...)} 是一次正常返回——事务照常提交，
 * 于是「钱扣了、红包没建成」这种半截状态就落库了。
 *
 * <p>携带的 {@link ApiResult} 就是要回给客户端的最终结果，由事务外层 catch 后原样返回，
 * 所以本异常既不需要 message 也刻意不抓栈（业务分支不是故障，抓栈纯属浪费）。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月03日
 */
public class RedPackBizException extends RuntimeException {

    private final ApiResult result;

    public RedPackBizException(ApiResult result) {
        // 关掉 suppression 与 writableStackTrace：这是控制流，不是异常现场
        super(String.valueOf(result.get("msg")), null, false, false);
        this.result = result;
    }

    public ApiResult getResult() {
        return this.result;
    }
}
