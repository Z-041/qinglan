package com.qinglan.common.storage;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * S3 兼容对象存储实现（AWS SDK v2 同步 client）。可对接自建 MinIO / 腾讯 COS / 阿里 OSS。
 *
 * <p>三点与 {@link LocalStorageService} 对齐，保证切换实现时上层无感：</p>
 * <ul>
 *   <li>返回 {@code /uploads/YYYY/MM/uuid.ext}，**对象 key 与 URL 去掉前缀后的路径段一致**</li>
 *   <li>文件名由服务端生成（UUID + 已校验扩展名），不含用户输入，天然免疫路径穿越</li>
 *   <li>{@code delete} 幂等：对象不存在 S3 也返回成功；任何失败只告警、不阻断主流程</li>
 * </ul>
 */
@Slf4j
public class S3StorageService implements StorageService {

    private final S3Client client;
    private final String bucket;

    public S3StorageService(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public String store(byte[] content, String extension) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        String relativeDir = "%d/%02d".formatted(today.getYear(), today.getMonthValue());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String key = relativeDir + "/" + fileName;
        client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentTypeOf(extension)).build(),
                RequestBody.fromBytes(content));
        return StorageUrl.PUBLIC_PREFIX + key;
    }

    @Override
    public void delete(String url) {
        String key = StorageUrl.objectKey(url).orElse(null);
        if (key == null) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception ex) {
            log.warn("删除对象失败 key={}，忽略以免阻断主流程", key, ex);
        }
    }

    @Override
    public Optional<StoredObject> load(String url) {
        String key = StorageUrl.objectKey(url).orElse(null);
        if (key == null) {
            return Optional.empty();
        }
        try (ResponseInputStream<GetObjectResponse> in = client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build())) {
            byte[] content = in.readAllBytes();
            GetObjectResponse meta = in.response();
            return Optional.of(new StoredObject(content, meta.lastModified(), meta.eTag()));
        } catch (S3Exception ex) {
            // 404 等价于「不存在」；其余（凭证/网络/权限）照抛，让上层返回 5xx 而不是伪装成 404
            if (ex.statusCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        } catch (IOException ex) {
            log.warn("读取对象失败 key={}", key, ex);
            return Optional.empty();
        }
    }

    @Override
    public boolean exists(String url) {
        String key = StorageUrl.objectKey(url).orElse(null);
        if (key == null) {
            return false;
        }
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private static String contentTypeOf(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }
}
