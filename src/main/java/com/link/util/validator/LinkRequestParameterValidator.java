package com.link.util.validator;

import com.link.im.common.enums.gloabl.GlobalCode;
import org.springframework.util.StringUtils;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月23日
 */
public class LinkRequestParameterValidator {


    public static boolean pageValidator(int skip,int limit){
        if (skip < 0 || limit <=0)
            return false;
        return true;
    }

    public static boolean stringValidator(String... params){
        boolean validator = true;
        for (String param : params) {
            if (StringUtils.isEmpty(param)){
                validator = false;
                break;
            }
        }
        return validator;
    }

}
