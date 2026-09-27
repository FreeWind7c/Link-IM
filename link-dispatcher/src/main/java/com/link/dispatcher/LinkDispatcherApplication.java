package com.link.dispatcher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author: 无敌代码写手
 * @CreateTime: 2026年08月30日
 */
@SpringBootApplication(
        scanBasePackages = {
                "com.link.core",
                "com.link.dispatcher"
        }
)
public class LinkDispatcherApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkDispatcherApplication.class, args);
    }

}
