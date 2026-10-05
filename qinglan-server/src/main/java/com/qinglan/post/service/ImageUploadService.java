package com.qinglan.post.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.ImageType;
import com.qinglan.common.storage.StorageService;
import com.qinglan.post.dto.UploadedImageResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 图片上传。
 *
 * <p>校验顺序：非空 → 大小 → 真实类型（按文件头字节，不看扩展名与 Content-Type）。
 * 通过后交给 {@link StorageService} 落盘，返回的地址再随发帖请求提交。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    /** 与 application.yml 的 spring.servlet.multipart.max-file-size 保持一致，双重把关 */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final StorageService storageService;

    public UploadedImageResponse upload(MultipartFile file) {
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
        return new UploadedImageResponse(storageService.store(content, type.extension()));
    }
}
