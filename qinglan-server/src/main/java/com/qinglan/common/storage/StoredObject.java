package com.qinglan.common.storage;

import java.time.Instant;

/**
 * 读回的存储对象：内容 + 可用于 HTTP 缓存协商的元数据。
 *
 * @param content      文件字节
 * @param lastModified 对象最后修改时间；拿不到时为 null（响应里就不下发 Last-Modified）
 * @param etag         对象 ETag；拿不到时为 null（响应里就不下发 ETag）
 */
public record StoredObject(byte[] content, Instant lastModified, String etag) {
}
