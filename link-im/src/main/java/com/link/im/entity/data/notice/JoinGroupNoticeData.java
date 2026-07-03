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
public class JoinGroupNoticeData extends NoticeData {

    private int type = 1;

    private NoticeUser inviter;

    private List<NoticeUser> joinUsers;

    private String text;



    @Data
    public static class NoticeUser{
        private String id;

        private String name;
    }
}
