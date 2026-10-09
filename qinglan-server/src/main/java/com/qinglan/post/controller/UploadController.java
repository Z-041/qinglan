package com.qinglan.post.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.web.ApiResponse;
import com.qinglan.post.dto.ImageUrlRefreshItem;
import com.qinglan.post.dto.ImageUrlRefreshRequest;
import com.qinglan.post.dto.UploadedImageResponse;
import com.qinglan.post.service.ImageUploadService;
import com.qinglan.post.service.ImageUrlAssembler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 图片上传与地址换取接口。均需登录（安全配置中仅放行了几个只读 GET）。
 *
 * <p>类型与大小在 {@link ImageUploadService} 里按文件头字节校验，不信任文件名与 Content-Type。</p>
 */
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final ImageUploadService imageUploadService;
    private final ImageUrlAssembler imageUrlAssembler;

    @PostMapping("/images")
    public ApiResponse<UploadedImageResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(imageUploadService.upload(CurrentUser.requireUserId(), file));
    }

    /**
     * 批量换取「当前 viewer 可直接使用」的图片地址（草稿恢复等场景）。
     *
     * <p>私有图在浏览器里 {@code <img>} 不带令牌，故草稿里保存的原 URL 取不到图，也不该把已过期的
     * 签名一路存下去。由前端在恢复草稿时调用本端点换取当前可用的地址；换取不到的（无权访问 / 已清理）
     * 明确标记 {@code available=false}，前端据此提示「需重新上传」，而不是静默破图。</p>
     *
     * <p>只对本人有权访问的对象签发：无权对象既不返回签名也不返回原 URL。</p>
     */
    @PostMapping("/image-urls")
    public ApiResponse<List<ImageUrlRefreshItem>> refreshImageUrls(
            @Valid @RequestBody ImageUrlRefreshRequest request) {
        return ApiResponse.ok(imageUrlAssembler.refresh(request.urls(), CurrentUser.requireUserId()));
    }
}
