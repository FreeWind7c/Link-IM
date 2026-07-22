package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月21日
 */
@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class CallData extends BaseData {

    // 0语音通话 1视频通话
    private int mediaType;

    // 0未接通 1已接通 2挂断 3拒绝
    private int status;

    private long startTime;

    private long endTime;
}
