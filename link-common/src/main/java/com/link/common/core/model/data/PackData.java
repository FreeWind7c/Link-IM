package com.link.common.core.model.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月16日
 */
@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PackData {
    private short magic;

    private short action;

    private int length;

    private byte[] body;

    public PackData(short action, int length, byte[] body) {
        this.action = action;
        this.length = length;
        this.body = body;
    }
}
