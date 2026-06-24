package com.link.util.print;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月18日
 */
@Slf4j
public class LinkPrintJsonUtil {

    public static void print(String title,Object obj,Class<?> clazz){
        Gson gson = new Gson();
        String json = gson.toJson(obj, clazz);
        log.info(title+":"+json);
    }

    public static void print(String title,Object obj){
        Gson gson = new Gson();
        String json = gson.toJson(obj, JSONObject.class);
        log.info(title+":"+json);
    }


}
