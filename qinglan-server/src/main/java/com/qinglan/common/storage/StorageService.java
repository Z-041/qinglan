package com.qinglan.common.storage;

import java.util.Optional;

/**
 * 文件存储抽象。
 *
 * <p>AGENTS.md 第九章要求「上传文件存对象存储，不存应用服务器本地磁盘」。生产用 {@link S3StorageService}
 * （S3 兼容协议，可对接自建 MinIO / 腾讯 COS / 阿里 OSS），本地开发与测试用 {@link LocalStorageService}。
 * 调用方（post 模块）只依赖本接口，不感知实现。</p>
 *
 * <p><b>URL 契约</b>：{@link #store} 返回的地址形如 {@code /uploads/YYYY/MM/uuid.ext}，前端
 * {@code <img src>} 直接使用、CSP 为 {@code img-src 'self'}。两种实现都必须返回同一形态的地址，
 * 且**对象 key 与 URL 去掉 {@code /uploads/} 前缀后的路径段一致**，图片下发端点据此反查归属。</p>
 */
public interface StorageService {

    /**
     * 保存文件，返回可供前端直接访问的 URL 路径。
     *
     * @param content   文件内容（已通过类型与大小校验）
     * @param extension 文件扩展名，不带点，如 {@code jpg}；由校验环节按真实图片类型判定，不可取自用户输入
     */
    String store(byte[] content, String extension);

    /** 删除文件；文件不存在时静默返回，避免删除失败阻断主流程 */
    void delete(String url);

    /**
     * 读回文件内容与元数据（图片下发端点用）。
     *
     * @return 对象不存在或地址非法时返回空，而不是抛异常——「取不到」在语义上等价于「不存在」
     */
    Optional<StoredObject> load(String url);

    /** 对象是否存在（发帖时校验引用对象确实已上传；不读内容） */
    boolean exists(String url);
}
