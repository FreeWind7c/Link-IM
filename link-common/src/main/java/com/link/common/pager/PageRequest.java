package com.link.common.pager;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月25日
 */
@Data
@Accessors(chain = true)
public class PageRequest {

    public static final int FLOW_LIMIT_DEFAULT = 20;

    public static final int FLOW_LIMIT_MAX = 100;
    /**
     * 游标：上一页最后一条流水的 id（ObjectId 十六进制串）。
     * 首页不传；之后每次把上一次返回的 nextCursor 原样带回来。
     * 注意游标分页没有 offset —— 位置由这条 id 锚定，不是「第几条」。
     */
    private String cursor;

    /** 每页条数，不传按 20，最大 100 */
    private Integer limit;
}
