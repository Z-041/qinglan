package com.qinglan.post.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageService;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.common.storage.StoredObject;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 受控图片下发（T-27①）。
 *
 * <p>原实现把上传目录当静态资源直接放行，于是「下架帖的图片仍可被公开直取」。改为本服务后，
 * 图片与帖子同口径判可见，且不可见与不存在**一律 404**，不泄露对象是否存在。</p>
 *
 * <p>可见性口径（复用 {@link Post#canBeViewedBy(Long)}，不另造一套）：</p>
 * <ul>
 *   <li>已被帖子引用：按所属帖子判定——已通过（APPROVED）人人可见；待审 / 驳回 / 下架仅作者可见</li>
 *   <li>未被任何帖子引用：仅上传者本人可见（上传后还没发帖的阶段，作者要能看到自己的图）</li>
 * </ul>
 *
 * <p><b>签名路径（短时令牌）</b>：浏览器 {@code <img>} 不带 Bearer 令牌，等价游客，故作者自己也取不到
 * 自己的私有图。为此下发 DTO 时给出签名 URL；请求若带合法的 {@code exp + sig}，则**不依赖 viewer 身份**
 * 直接放行（签名合法/过期/超上限的判定见 {@link ImageUrlSigner}）。签名非法/过期一律与「不存在」同码 404。
 * 无签名（或只带其一）时回落到上面的身份判定路径，行为不变。</p>
 *
 * <p>查询代价：每次取图 = 1 次 {@code uploaded_file} 点查（唯一键）+ 已绑定时 1 次 {@code post}
 * 主键点查；签名路径跳过这两次查库，直接读对象。**不是 N+1**。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageDeliveryService {

    private final StorageService storageService;
    private final ImageUrlSigner imageUrlSigner;
    private final UploadedFileRepository uploadedFileRepository;
    private final PostRepository postRepository;

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

        if (imageUrlSigner.isSigned(expRaw, sigRaw)) {
            if (!imageUrlSigner.verify(path, expRaw, sigRaw)) {
                // 签名非法 / 过期 / 超上限 —— 与「不存在」同码同状态，不给攻击者区分信号
                throw new BizException(ErrorCode.IMAGE_NOT_FOUND);
            }
            // 签名合法即放行，不查库、不看 viewer
            return read(path, contentType);
        }

        String objectKey = StorageUrl.objectKey(path)
                .orElseThrow(() -> new BizException(ErrorCode.IMAGE_NOT_FOUND));
        UploadedFile file = uploadedFileRepository.findByObjectKey(objectKey)
                .orElseThrow(() -> new BizException(ErrorCode.IMAGE_NOT_FOUND));
        if (!visibleTo(file, viewerId)) {
            throw new BizException(ErrorCode.IMAGE_NOT_FOUND);
        }
        return read(path, contentType);
    }

    private DeliveredImage read(String path, String contentType) {
        StoredObject stored = storageService.load(path)
                .orElseThrow(() -> new BizException(ErrorCode.IMAGE_NOT_FOUND));
        return new DeliveredImage(stored.content(), contentType, stored.lastModified(), stored.etag());
    }

    private boolean visibleTo(UploadedFile file, Long viewerId) {
        if (!file.isBound()) {
            return file.isUploadedBy(viewerId);
        }
        Optional<Post> post = postRepository.findById(file.getBoundPostId());
        // 帖子已被物理删除（当前无此路径，留作兜底）→ 视为不可见
        return post.map(value -> value.canBeViewedBy(viewerId)).orElse(false);
    }

    /**
     * 下发的图片：内容 + Content-Type（由扩展名推出）+ 缓存协商元数据。
     */
    public record DeliveredImage(byte[] content, String contentType, Instant lastModified, String etag) {
    }
}
