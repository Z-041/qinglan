package com.qinglan.post.controller;

import com.qinglan.common.security.CurrentUser;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.post.service.ImageDeliveryService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 受控图片下发端点，接管 {@code GET /uploads/**}（原静态资源映射已移除，见 StorageConfig）。
 *
 * <p>为什么不再用静态映射：静态资源无法按访问者判可见，导致「下架帖的图片仍可被公开直取」（T-27①）。
 * 改由本端点下发后，本地与生产行为一致、可见性规则本地也能测。</p>
 *
 * <p>安全响应头（CSP / nosniff / X-Frame-Options）由 {@code SecurityConfig} 统一为所有响应下发，
 * 本端点不另设，避免两处口径漂移（AGENTS.md 第九章）。</p>
 */
@RestController
@RequiredArgsConstructor
public class UploadedImageController {

    /**
     * 短 TTL：图片必须随帖子下架而**立即**失效，长缓存会把已下架的图留在浏览器里。
     * 用 {@code private}——响应随访问者身份变化（作者可见、游客 404），共享缓存不能复用。
     * 代价是无法被 CDN 长缓存，属本方案明示的取舍。
     */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ImageDeliveryService imageDeliveryService;

    @GetMapping("/uploads/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        // 直接取原始 requestURI（未解码、不含查询串）：让形态校验面对真实输入，避免解码后再被绕过
        String path = request.getRequestURI();
        // 私有图片的短时签名 URL：exp/sig 从查询串取，校验通过即放行（不依赖 viewer 身份）
        ImageDeliveryService.DeliveredImage image = imageDeliveryService.load(
                path,
                request.getParameter(StorageUrl.PARAM_EXP),
                request.getParameter(StorageUrl.PARAM_SIG),
                CurrentUser.currentUserId().orElse(null));

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.maxAge(CACHE_TTL).cachePrivate());
        if (image.etag() != null) {
            builder.header(HttpHeaders.ETAG, quoted(image.etag()));
        }
        if (image.lastModified() != null) {
            builder.lastModified(image.lastModified());
        }
        return builder.body(image.content());
    }

    /** ETag 必须以引号包裹（强校验器），存储实现给出的值格式不一，这里统一补齐 */
    private static String quoted(String etag) {
        return etag.startsWith("\"") || etag.startsWith("W/") ? etag : "\"" + etag + "\"";
    }
}
