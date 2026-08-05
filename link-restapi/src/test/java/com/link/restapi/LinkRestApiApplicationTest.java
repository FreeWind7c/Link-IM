package com.link.restapi;

import com.link.im.constants.wallet.WalletTopUpStatusKeys;
import com.link.im.entity.wallet.WalletTopUp;
import com.link.im.util.MD5Util;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.math.BigDecimal;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月02日
 */
@SpringBootTest(classes = LinkRestApiApplication.class)
public class LinkRestApiApplicationTest {


    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    public void unit1(){
        String userId = "6a6a3c2404b78eb884c0f776";
        WalletTopUp walletTopUp = new WalletTopUp();
        walletTopUp.setUserId(new ObjectId(userId));
        walletTopUp.setAmount(new BigDecimal(500));
        walletTopUp.setSecretKey(MD5Util.encrypt(userId+ WalletTopUp.MD5_SECRET));
        walletTopUp.setStatus(WalletTopUpStatusKeys.UNREDEEMED);
        walletTopUp.setCreatedTime(System.currentTimeMillis());
        this.mongoTemplate.insert(walletTopUp);
    }
}
