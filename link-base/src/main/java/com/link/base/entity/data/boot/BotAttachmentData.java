package com.link.base.entity.data.boot;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月11日
 */
@Data
@ToString(callSuper = true)
public class BotAttachmentData {

    // 1图片 2视频 3音频 4文件
    private int type;

    private String url;

}
