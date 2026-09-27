package com.link.base.entity.data.notice;

import com.link.base.entity.data.NoticeData;
import com.link.base.provider.NoticeDataProvider;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月30日
 */
@Data
@Accessors(chain = true)
public class RemoveGroupNoticeData extends NoticeData implements NoticeDataProvider {

    private JoinGroupNoticeData.NoticeUser operationUser;

    private List<JoinGroupNoticeData.NoticeUser> removeUsers;

    @Override
    public int getDataType() {
        return 3;
    }

    @Override
    public String getTemplate() {
        return "移除群聊";
    }


    @Data
    public static class NoticeUser{
        private String id;

        private String name;
    }
}
