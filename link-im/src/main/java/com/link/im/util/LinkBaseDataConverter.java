package com.link.im.util;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.link.im.entity.base.BaseData;
import com.link.im.entity.data.ImageData;
import com.link.im.entity.data.TextData;
import com.link.im.entity.data.VideoData;
import com.link.im.entity.message.type.MessageType;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年07月15日
 */
public class LinkBaseDataConverter {



    public static BaseData convererBaseData(String data){
        JSONObject message = JSONObject.parseObject(data);

        switch (message.getIntValue("type")){
            case 1001:
                return converterTextData(data);
        }
        return null;
    }


    public static TextData converterTextData(String data){
        Gson gson = new Gson();
        TextData text = gson.fromJson(data, TextData.class);
        return text;
    }

    public static ImageData converterImageData(String data){
        Gson gson = new Gson();
        ImageData image = gson.fromJson(data, ImageData.class);
        return image;
    }

    public static VideoData converterVideoData(String data){
        Gson gson = new Gson();
        VideoData video = gson.fromJson(data, VideoData.class);
        return video;
    }

}
