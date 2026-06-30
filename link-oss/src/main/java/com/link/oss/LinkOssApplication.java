package com.link.oss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * OSS 资源服务启动类。独立 HTTP 进程，默认端口 9000，提供对象存储上传等接口。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@SpringBootApplication
public class LinkOssApplication {
    public static void main(String[] args) {
        SpringApplication.run(LinkOssApplication.class, args);
    }
}
