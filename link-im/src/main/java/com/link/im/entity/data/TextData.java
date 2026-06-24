package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.ToString;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@ToString(callSuper = true)
public class TextData extends BaseData {
    private String content;
}
