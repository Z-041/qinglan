package com.qinglan.common.storage;

/**
 * 文件存储抽象。
 *
 * <p>AGENTS.md 第九章要求「上传文件存对象存储，不存应用服务器本地磁盘」。本机没有 Docker、
 * 起不了 MinIO，故 P1 提供本地实现先把链路跑通，生产切对象存储时只需换实现类，
 * 调用方代码不动——与 P0 用 InMemory/Redis 两套验证码存储是同一思路。</p>
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
}
