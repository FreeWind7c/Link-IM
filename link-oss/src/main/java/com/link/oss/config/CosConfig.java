package com.link.oss.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 客户端配置。把 COSClient 作为单例 Bean 交给容器管理：
 * COSClient 内部维护连接池、线程安全，应全局复用，切忌每次上传新建。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Configuration
@RequiredArgsConstructor
public class CosConfig {

    private final TencentCosProperties properties;

    /**
     * destroyMethod = "shutdown"：容器销毁时关闭 COSClient，释放连接池与后台线程。
     */
    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient() {
        COSCredentials cred = new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        // 强制 HTTPS 传输
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(cred, clientConfig);
    }
}
