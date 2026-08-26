package com.link.im.entity.data.notice;

import com.link.im.entity.data.NoticeData;
import com.link.im.provider.NoticeDataProvider;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月24日
 */
@Data
@Accessors(chain = true)
public class CreateGroupNoticeData extends NoticeData implements NoticeDataProvider {

    private String creatorId;

    @Override
    public int getDataType() {
        return 4;
    }

    @Override
    public String getTemplate() {
        return "发起了群聊";
    }

}
