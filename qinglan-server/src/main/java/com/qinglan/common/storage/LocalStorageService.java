package com.qinglan.common.storage;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    /** 对外访问前缀，与 {@link StorageWebConfig} 的映射保持一致 */
    private static final String PUBLIC_PREFIX = "/uploads";

    private final Path root;

    public LocalStorageService(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String relativeDir = "%d/%02d".formatted(today.getYear(), today.getMonthValue());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = root.resolve(relativeDir).resolve(fileName).normalize();
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
        return PUBLIC_PREFIX + "/" + relativeDir + "/" + fileName;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(PUBLIC_PREFIX + "/")) {
            return;
        }
        Path target = root.resolve(url.substring(PUBLIC_PREFIX.length() + 1)).normalize();
        if (!target.startsWith(root)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("删除文件失败 path={}", target, ex);
        }
    }
}
