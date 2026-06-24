package com.link;

import com.link.core.config.LinkCoreConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LinkImApplicationTests {


    @Autowired
    private LinkCoreConfig linkCoreConfig;

    @Test
    void contextLoads() {
        System.out.println(linkCoreConfig.getPort());
        System.out.println(linkCoreConfig.getSoRcvBuf());
    }

}
