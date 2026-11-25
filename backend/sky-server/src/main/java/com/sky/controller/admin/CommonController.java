package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("admin/common")
@Slf4j
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    private static final List<String> ALLOWED_SUFFIXES = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp");
    private static final long MAX_SIZE = 10 * 1024 * 1024L;

    /**
     * 文件上传
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：{}", file);
        if (file == null || file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }
        // 获取原始文件名并截取后缀
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            return Result.error("文件格式不正确");
        }
        String suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_SUFFIXES.contains(suffix)) {
            return Result.error("仅支持上传 jpg、jpeg、png、gif、bmp、webp 图片");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.error("文件大小不能超过 10MB");
        }
        try {
            String objectName = UUID.randomUUID() + suffix;
            String fileUrl = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(fileUrl);
        } catch (Exception e) {
            log.error("文件上传失败：{}", e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }
}
