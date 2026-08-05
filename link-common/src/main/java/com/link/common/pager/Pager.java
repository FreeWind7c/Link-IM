package com.link.common.pager;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@Data
@Accessors(chain = true)
public class Pager<T> {

    private String nextCursor;

    private boolean hasMore;

    private List<T> list;

}
