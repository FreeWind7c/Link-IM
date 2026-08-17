package com.link.im.entity.data.notice;

import com.link.im.entity.data.NoticeData;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Data
@Accessors(chain = true)
public class TrtcCallNoticeData extends NoticeData {
    private int type = 2;

    private String text;
}
