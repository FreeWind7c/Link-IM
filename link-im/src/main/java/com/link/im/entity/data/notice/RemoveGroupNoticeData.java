package com.link.im.entity.data.notice;

import com.link.im.entity.data.NoticeData;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月30日
 */
@Data
@Accessors(chain = true)
public class RemoveGroupNoticeData extends NoticeData {
    private int type = 2;

    private JoinGroupNoticeData.NoticeUser operationUser;

    private List<JoinGroupNoticeData.NoticeUser> removeUsers;

    private String text;
    @Data
    public static class NoticeUser{
        private String id;

        private String name;
    }
}
