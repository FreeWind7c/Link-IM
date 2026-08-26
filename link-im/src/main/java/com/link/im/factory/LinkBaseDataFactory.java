package com.link.im.factory;

import com.link.im.entity.data.NoticeData;
import com.link.im.entity.data.TextData;
import com.link.im.entity.data.notice.CreateGroupNoticeData;
import com.link.im.entity.data.notice.DefaultNoticeData;
import com.link.im.entity.data.notice.JoinGroupNoticeData;
import com.link.im.entity.data.notice.RemoveGroupNoticeData;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月24日
 */

@Component
public class LinkBaseDataFactory {


    public NoticeData createDefaultNoticeData(String chatId,String text){
        DefaultNoticeData data = new DefaultNoticeData();
        return createNoticeData(chatId,text,data.getDataType(),data);
    }

    public NoticeData createRemoveGroupNoticeData(String chatId, JoinGroupNoticeData.NoticeUser operationUser, List<JoinGroupNoticeData.NoticeUser> removeUsers){
        RemoveGroupNoticeData data = new RemoveGroupNoticeData();
        data.setOperationUser(operationUser);
        data.setRemoveUsers(removeUsers);
        return createNoticeData(chatId,data.getTemplate(),data.getDataType(),data);
    }

    public NoticeData createJoinNoticeData(String chatId,JoinGroupNoticeData.NoticeUser inviter, List<JoinGroupNoticeData.NoticeUser> joinUser){
        JoinGroupNoticeData data = new JoinGroupNoticeData();
        data.setInviter(inviter);
        data.setJoinUsers(joinUser);
        return createNoticeData(chatId,data.getTemplate(),data.getDataType(),data);
    }

    public NoticeData createGroupNoticeData(String chatId,String creatorId){
        CreateGroupNoticeData data = new CreateGroupNoticeData().setCreatorId(creatorId);
        System.out.println("text:" + data.getTemplate());
        return createNoticeData(chatId, data.getTemplate(),data.getDataType(),data);
    }

    public TextData createTextData(String content){
        TextData data = new TextData().setContent(content);
        return data;
    }

    private NoticeData createNoticeData(String chatId, String text, int dataType, NoticeData noticeData) {
        NoticeData data = new NoticeData()
                .setType(dataType)
                .setChatId(chatId)
                .setText(text)
                .setData(noticeData);
        return data;
    }

}
