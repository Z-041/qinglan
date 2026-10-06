package com.qinglan.post.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.post.dto.UploadedImageResponse;
import com.qinglan.post.service.ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片上传接口。需要登录（安全配置中仅放行了几个只读 GET）。
 *
 * <p>类型与大小在 {@link ImageUploadService} 里按文件头字节校验，不信任文件名与 Content-Type。</p>
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final ImageUploadService imageUploadService;

    @PostMapping("/images")
    public ApiResponse<UploadedImageResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(imageUploadService.upload(CurrentUser.requireUserId(), file));
    }
}
