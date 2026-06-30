package com.link.oss.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 腾讯云 COS 配置项。值从 application.yml 的 link.oss.cos.* 注入，
 * 而 yml 里再用 ${ENV:默认} 从环境变量读取——密钥绝不硬编码进代码/仓库。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Data
@Component
@ConfigurationProperties(prefix = "link.oss.cos")
public class TencentCosProperties {

    /** 腾讯云访问密钥 ID（SecretId） */
    private String secretId;

    /** 腾讯云访问密钥 Key（SecretKey） */
    private String secretKey;

    /** 存储桶所在地域，如 ap-guangzhou */
    private String region;

    /** 存储桶名称，格式 BucketName-APPID，如 mybucket-1250000000 */
    private String bucketName;

    /**
     * 访问域名前缀（不带末尾斜杠），用于拼接上传后可访问的 URL。
     * 如 https://mybucket-1250000000.cos.ap-guangzhou.myqcloud.com 或自定义/CDN 域名。
     */
    private String baseUrl;

    /** 对象 key 的目录前缀，如 im/。可空。 */
    private String pathPrefix = "";
}
