package com.link.im.entity.data.notice;

import com.link.im.entity.data.NoticeData;
import com.link.im.provider.NoticeDataProvider;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月30日
 */
@Data
@Accessors(chain = true)
public class JoinGroupNoticeData extends NoticeData implements NoticeDataProvider {

    private NoticeUser inviter;

    private List<NoticeUser> joinUsers;

    @Override
    public int getDataType() {
        return 2;
    }

    @Override
    public String getTemplate() {
        return "加入群聊";
    }


    @Data
    public static class NoticeUser{
        private String id;

        private String name;
    }
}
