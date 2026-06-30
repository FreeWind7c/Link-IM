package com.link.oss.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * link-oss 自包含的统一返回体。本模块不依赖 link-im（避免拖入 mongo/netty/core），
 * 故不复用 im 的 R，自带一个轻量结构。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Data
@Accessors(chain = true)
public class OssResult {

    private int code;
    private String msg;
    private Object data;

    public static OssResult ok(Object data) {
        return new OssResult().setCode(200).setMsg("success").setData(data);
    }

    public static OssResult error(String msg) {
        return new OssResult().setCode(500).setMsg(msg);
    }
}
