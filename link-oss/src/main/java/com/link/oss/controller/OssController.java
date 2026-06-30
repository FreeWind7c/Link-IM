package com.link.oss.controller;

import com.link.oss.model.OssResult;
import com.link.oss.service.OssService;
import com.link.oss.util.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OSS 上传接口。
 *
 * @Author: 无敌代码写手
 * @CreateTime: 2026年06月25日
 */
@Slf4j
@RestController
@RequestMapping("/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    /**
     * 上传文件，返回可访问 URL。
     * 前端用 multipart/form-data，字段名 file。
     */
    @PostMapping("/upload")
    public R upload(@RequestParam("file") MultipartFile file) {
        try {
            String url = ossService.upload(file);
            return R.ok().setData(url);
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传接口异常", e);
            return R.error("上传失败");
        }
    }
}
