package com.qinglan.common.storage;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * 本地磁盘实现，仅用于本机开发与测试。
 *
 * <p>两个安全要点：</p>
 * <ul>
 *   <li>文件名完全由服务端生成（UUID + 已校验的扩展名），不含任何用户输入，天然免疫路径穿越</li>
 *   <li>按年月分目录，避免单目录文件数过多</li>
 * </ul>
 */
@Slf4j
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String relativeDir = "%d/%02d".formatted(today.getYear(), today.getMonthValue());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String objectKey = relativeDir + "/" + fileName;
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            // 理论上不可能触发，留作兜底：一旦目录拼接被污染，宁可失败也不写到根目录之外
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            log.error("写入文件失败 path={}", target, ex);
            throw new BizException(ErrorCode.STORAGE_ERROR);
        }
        // 对外 URL 由 StorageUrl 统一拼装（与解析同源，形态不会两处漂移）
        return StorageUrl.publicUrl(objectKey);
    }

    @Override
    public void delete(String url) {
        Path target = resolve(url);
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("删除文件失败 path={}", target, ex);
        }
    }

    @Override
    public Optional<StoredObject> load(String url) {
        Path target = resolve(url);
        if (target == null || !Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            byte[] content = Files.readAllBytes(target);
            Instant lastModified = Files.getLastModifiedTime(target).toInstant();
            // 本地实现无对象 ETag，用「长度-修改时间」生成一个弱标识，够做 HTTP 缓存协商
            String etag = "\"" + content.length + "-" + lastModified.toEpochMilli() + "\"";
            return Optional.of(new StoredObject(content, lastModified, etag));
        } catch (IOException ex) {
            log.warn("读取文件失败 path={}", target, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String url) {
        Path target = resolve(url);
        return target != null && Files.isRegularFile(target);
    }

    /**
     * 把对外 URL 还原为磁盘路径；地址非法或解析到根目录之外时返回 null。
     *
     * <p>URL 形态统一由 {@link StorageUrl} 解析（与 S3 实现同口径，只接受本站生成的
     * {@code /uploads/yyyy/MM/32hex.ext}）。{@code normalize()} 后再确认结果仍在 root 之内——
     * 冗余的纵深防御，与 {@link #store} 里的同类兜底一致。</p>
     */
    private Path resolve(String url) {
        String objectKey = StorageUrl.objectKey(url).orElse(null);
        if (objectKey == null) {
            return null;
        }
        Path target = root.resolve(objectKey).normalize();
        return target.startsWith(root) ? target : null;
    }
}
