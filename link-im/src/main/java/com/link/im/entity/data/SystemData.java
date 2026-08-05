package com.link.im.entity.data;

import com.link.im.entity.base.BaseData;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月12日
 */
@Data
@Accessors(chain = true)
public abstract class SystemData extends BaseData {

    private final int action;

    public SystemData(int action){
        this.action = action;
    }
}
