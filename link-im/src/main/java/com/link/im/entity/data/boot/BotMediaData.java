package com.link.im.entity.data.boot;

import com.link.im.entity.base.BaseBotData;
import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月11日
 */
@Data
@ToString(callSuper = true)
public class BotMediaData  {

    // 1图片 2视频 3音频
    private int type;


    private List<String> urls;

}
