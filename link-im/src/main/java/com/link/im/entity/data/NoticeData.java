package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月30日
 */
@Data
@Accessors(chain = true)
public class NoticeData extends BaseData {
    private String chatId;

    private NoticeData data;
}
