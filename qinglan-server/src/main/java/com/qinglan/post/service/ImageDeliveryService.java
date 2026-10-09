package com.qinglan.post.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.StorageService;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.common.storage.StoredObject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 受控图片下发（T-27①）。
 *
 * <p>原实现把上传目录当静态资源直接放行，于是「下架帖的图片仍可被公开直取」。改为本服务后，
 * 图片与帖子同口径判可见，且不可见与不存在**一律 404**，不泄露对象是否存在。</p>
 *
 * <p>访问判定（可读 / 签名是否有效）已收敛到 {@link ImageAccessService}，本类退化为
 * 「授权 + 读字节」的薄适配器：{@link ImageAccessService#canDeliver} 通过即从 {@link StorageService}
 * 取回内容。可见性口径见 {@link ImageAccessService}。</p>
 *
 * <p>查询代价：签名合法时跳过所有查库，直接读对象；否则由 {@code ImageAccessService} 做
 * 1 次 {@code uploaded_file} 点查（+ 已绑定时 1 次 {@code post} 点查）。**不是 N+1**。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageDeliveryService {

    private final StorageService storageService;
    private final ImageAccessService imageAccessService;

    /**
     * @param path     请求路径（{@code /uploads/...}，不含查询串），由 Controller 传 {@code getRequestURI()}
     * @param expRaw   查询参数 {@code exp}；缺省为 null
     * @param sigRaw   查询参数 {@code sig}；缺省为 null
     * @param viewerId 当前访问者 id；游客为 null
     */
    @Transactional(readOnly = true)
    public DeliveredImage load(String path, String expRaw, String sigRaw, Long viewerId) {
        // 严格校验 URL 形态：只接受本站生成的 /uploads/{yyyy}/{MM}/{32hex}.{ext}，
        // 其余（含 .. 、双斜杠、绝对路径、任意路径段）一律按「不存在」处理
        String contentType = StorageUrl.contentType(path)
                .orElseThrow(() -> new BizException(ErrorCode.IMAGE_NOT_FOUND));
        // 签名非法 / 过期 / 超上限 / 无权访问 —— 与「不存在」同码同状态，不给攻击者区分信号
        if (!imageAccessService.canDeliver(path, expRaw, sigRaw, viewerId)) {
            throw new BizException(ErrorCode.IMAGE_NOT_FOUND);
        }
        return read(path, contentType);
    }

    private DeliveredImage read(String path, String contentType) {
        StoredObject stored = storageService.load(path)
                .orElseThrow(() -> new BizException(ErrorCode.IMAGE_NOT_FOUND));
        return new DeliveredImage(stored.content(), contentType, stored.lastModified(), stored.etag());
    }

    /**
     * 下发的图片：内容 + Content-Type（由扩展名推出）+ 缓存协商元数据。
     */
    public record DeliveredImage(byte[] content, String contentType, Instant lastModified, String etag) {
    }
}
