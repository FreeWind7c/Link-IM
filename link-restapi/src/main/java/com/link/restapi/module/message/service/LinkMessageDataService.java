package com.link.restapi.module.message.service;

import com.link.base.entity.base.BaseMessage;
import com.link.base.entity.data.CallData;
import com.link.base.entity.data.RedPacketData;
import com.link.base.entity.message.type.MessageType;
import com.link.base.entity.redpack.RedPacket;
import com.link.base.entity.redpack.RedPacketRecord;
import com.link.base.entity.rtc.TrtcCallInfo;
import com.link.base.mongo.BasePlatFormMongoService;

import com.link.base.provider.MessageData;
import com.link.restapi.module.message.model.vo.LinkMessageInfoVo;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月19日
 */

@Component
public class LinkMessageDataService extends BasePlatFormMongoService<MessageData> {

    @Autowired
    private MongoTemplate mongoTemplate;

    public <T extends MessageData> Map<ObjectId, T> pullBaseData (List<? extends BaseMessage> messages, Class<T> clazz) {
        List<ObjectId> msgIds = messages.stream().map(item -> { return item.getId();
        }).collect(Collectors.toList());
        List<T> items = this.getMongoTemplate().find(eq( where("message_id").in(msgIds)), clazz);
        if (items != null && !items.isEmpty()){
            return items.stream().collect(Collectors.toMap(T::getMessageId, Function.identity()));
        }
        return null;
    }

    public <T extends MessageData> void createData(BaseMessage item, LinkMessageInfoVo vo,
                                                   Map<Integer, Map<ObjectId, ? extends MessageData>> map) {
        if (item.getType() == MessageType.RTC_CALL_MESSAGE.getType())
        {
            Map<ObjectId, ? extends MessageData> dataMap = map.get(MessageType.RTC_CALL_MESSAGE.getType());
            TrtcCallInfo trtcCallInfo = (TrtcCallInfo) dataMap.get(item.getId());
            CallData data = CallData.toData(trtcCallInfo);
            vo.setData(data.toJson());
        }
        else if (item.getType() == MessageType.RED_PACK_MESSAGE.getType())
        {
            Map<ObjectId, ? extends MessageData> dataMap = map.get(MessageType.RED_PACK_MESSAGE.getType());
            RedPacket packet = (RedPacket) dataMap.get(item.getId());
            Query query = new Query(Criteria.where("packet_id").is(packet.getId()));
            query.fields().include("user_id");
            List<RedPacketRecord> records = this.mongoTemplate.find(query, RedPacketRecord.class);
            Set<String> userId = records.stream().map(v -> {
                return v.getUserId().toHexString();
            }).collect(Collectors.toSet());
            RedPacketData data = RedPacketData.toData(packet, userId);

            vo.setData(data.toJson());
        }
    }
}
