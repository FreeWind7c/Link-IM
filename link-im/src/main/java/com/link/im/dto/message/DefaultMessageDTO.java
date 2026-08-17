package com.link.im.dto.message;

import com.google.gson.Gson;
import com.link.im.entity.base.BaseMessageDTO;
import com.link.im.entity.message.DefaultMessageInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月12日
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class DefaultMessageDTO extends BaseMessageDTO {


}
