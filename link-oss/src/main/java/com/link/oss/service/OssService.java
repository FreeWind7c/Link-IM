package com.link.oss.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.link.oss.config.TencentCosProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.UUID;

/**
 * COS 上传服务。负责生成对象 key、调 COSClient 上传、拼接可访问 URL。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final COSClient cosClient;
    private final TencentCosProperties properties;

    /**
     * 上传文件到 COS，返回可访问的完整 URL。
     *
     * @param file 上传的文件（Spring MultipartFile）
     * @return 文件可访问 URL
     */
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }

        String key = buildObjectKey(file.getOriginalFilename());

        ObjectMetadata meta = new ObjectMetadata();
        // 必须设置长度，否则 SDK 会把整个流读进内存来确定长度
        meta.setContentLength(file.getSize());
        if (file.getContentType() != null) {
            meta.setContentType(file.getContentType());
        }

        try (InputStream in = file.getInputStream()) {
            PutObjectRequest request = new PutObjectRequest(properties.getBucketName(), key, in, meta);
            cosClient.putObject(request);
        } catch (IOException e) {
            log.error("读取上传文件流失败 key={}", key, e);
            throw new RuntimeException("上传失败：读取文件流异常", e);
        } catch (Exception e) {
            // 捕获 COS 抛出的各类异常（CosClientException / CosServiceException）
            log.error("COS 上传失败 key={}", key, e);
            throw new RuntimeException("上传失败：" + e.getMessage(), e);
        }

        String url = properties.getBaseUrl() + "/" + key;
        log.info("COS 上传成功 key={}, url={}", key, url);
        return url;
    }

    /**
     * 生成对象 key：前缀 + 日期目录 + UUID + 原扩展名。
     * 用 UUID 避免重名覆盖，按日期分目录便于管理。
     */
    private String buildObjectKey(String originalFilename) {
        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0) {
                ext = originalFilename.substring(dot);
            }
        }
        String date = LocalDate.now().toString(); // yyyy-MM-dd
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String prefix = properties.getPathPrefix();
        if (prefix == null) {
            prefix = "";
        }
        // 规整：去掉前缀可能带的首尾斜杠，统一拼成 prefix/date/uuid.ext
        prefix = prefix.replaceAll("^/+|/+$", "");
        StringBuilder sb = new StringBuilder();
        if (!prefix.isEmpty()) {
            sb.append(prefix).append('/');
        }
        sb.append(date).append('/').append(uuid).append(ext);
        return sb.toString();
    }
}
