package com.link.base.entity.data.notice;

import com.link.base.entity.data.NoticeData;
import com.link.base.provider.NoticeDataProvider;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月17日
 */
@Data
@Accessors(chain = true)
public class DefaultNoticeData extends NoticeData implements NoticeDataProvider {


    @Override
    public int getDataType() {
        return 1;
    }

    @Override
    public String getTemplate() {
        return null;
    }
}
