package com.link.im.handler.base;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月24日
 */
@Slf4j
public class BasePlatformEventHandler {

    public void printf(String title,Object obj,Class<?> clazz){
        Gson gson = new Gson();
        String json = gson.toJson(obj, clazz);
        log.info(title+json);
    }

}
