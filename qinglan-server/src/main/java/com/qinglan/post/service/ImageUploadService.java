package com.qinglan.post.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.ImageType;
import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageService;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.common.store.EphemeralStore;
import com.qinglan.post.config.UploadProperties;
import com.qinglan.post.dto.UploadedImageResponse;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.UploadedFileRepository;
import com.qinglan.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片上传。
 *
 * <p>校验顺序：可写状态 → 频次 → 非空 → 大小 → 真实类型（按文件头字节，不看扩展名与 Content-Type）。
 * 通过后交给 {@link StorageService} 落盘，返回的地址再随发帖请求提交。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    /** 与 application.yml 的 spring.servlet.multipart.max-file-size 保持一致，双重把关 */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    /** 限流键按用户维度（AGENTS.md Redis key 约定 ql:{domain}:{id}） */
    private static final String RATE_KEY_PREFIX = "ql:upload:rate:";

    private final StorageService storageService;
    private final ImageUrlSigner imageUrlSigner;
    private final UserService userService;
    private final EphemeralStore ephemeralStore;
    private final UploadProperties uploadProperties;
    private final UploadedFileRepository uploadedFileRepository;

    /**
     * 上传并**登记归属**（T-12'）：落一行 {@code uploaded_file}，发帖时据此校验「属于本人、未被引用」。
     *
     * <p>落库与写对象不在同一个存储事务里：若登记失败，存储里会留一个未登记的对象，但没有任何地方
     * 引用它，属孤儿（无人可见、也不可被引用），由回收任务清理——这是刻意的取舍，宁可留孤儿也不
     * 产生「有登记行、对象却不存在」的坏引用。</p>
     */
    @Transactional
    public UploadedImageResponse upload(Long userId, MultipartFile file) {
        // 上传同样属于写操作：封禁/黑名单期内不得再写入任何内容
        userService.requireUsableById(userId);
        // 限流在格式校验之前：非法请求同样消耗配额，避免用坏文件绕过频次限制刷存储
        long attempts = ephemeralStore.increment(RATE_KEY_PREFIX + userId, uploadProperties.window());
        if (attempts > uploadProperties.maxPerWindow()) {
            throw new BizException(ErrorCode.UPLOAD_TOO_FREQUENT);
        }
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD_INVALID);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            log.warn("读取上传文件失败，size={}", file.getSize(), ex);
            throw new BizException(ErrorCode.UPLOAD_INVALID);
        }
        if (content.length > MAX_IMAGE_BYTES) {
            throw new BizException(ErrorCode.UPLOAD_INVALID);
        }
        ImageType type = ImageType.detect(content)
                .orElseThrow(() -> new BizException(ErrorCode.UPLOAD_INVALID));
        String url = storageService.store(content, type.extension());
        // 登记归属：URL 形态由 StorageService 保证，objectKey 拿不到即实现违约，宁可失败也不登记一条坏数据
        String objectKey = StorageUrl.objectKey(url)
                .orElseThrow(() -> new BizException(ErrorCode.STORAGE_ERROR));
        uploadedFileRepository.save(UploadedFile.of(userId, objectKey));
        // 未绑定上传属私有：返回签名 URL，让发帖页预览（浏览器 <img> 不带令牌）即刻可取。
        // 前端把它填进发帖请求后，服务端仍按 objectKey 归一（签名查询串被忽略），落库的是干净 URL。
        return new UploadedImageResponse(imageUrlSigner.sign(url));
    }
}
