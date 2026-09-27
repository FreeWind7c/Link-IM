package com.link.restapi.config.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;

public class SentinelBlockHandler {

    public static Object handle(BlockException e) {
        throw new RuntimeException("请求过于频繁，请稍后再试");
    }
}