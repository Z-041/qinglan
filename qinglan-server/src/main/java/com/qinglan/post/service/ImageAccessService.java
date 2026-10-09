package com.qinglan.post.service;

import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 图片访问判定的**唯一出处**：一张上传图「对谁可见」以及「对外该给干净 URL 还是短时签名 URL」。
 *
 * <p>此前这条概念分散在 {@code ImageDeliveryService.visibleTo}、{@code ImageUrlAssembler.forPost}
 * 与 {@code ImageUrlAssembler.refreshOne} 三处各写一遍——改一次隐私口径要动三处，且极易漂移。
 * 收敛到本模块后，{@link ImageUrlSigner}（HMAC 原语）与 {@link StorageUrl}（地址解析）降为内部适配器，
 * 不出现在接口上；下发端点与各装配处都退化为本模块的调用方。</p>
 *
 * <p><b>一条规则</b>：仅 {@code APPROVED} 对所有人公开（给干净 URL、可共享缓存）；其余状态
 * （待审 / 驳回 / 下架，以及未绑定上传）属私有——只有作者或上传者可读，对外一律给短时签名 URL。
 * 读取授权复用 {@link Post#canBeViewedBy(Long)}，不另造一套。</p>
 *
 * <p><b>性能契约</b>：列表类调用点手里已持有帖子状态，走 {@link #forPost} 免查库（不产生 N+1）；
 * 只有按 {@code objectKey} 反查的场景（批量换取、下发端点）才走 {@link #resolve}，代价为
 * 1 次 {@code uploaded_file} 点查 + 绑定时 1 次 {@code post} 主键点查。</p>
 */
@Service
@RequiredArgsConstructor
public class ImageAccessService {

    private final ImageUrlSigner imageUrlSigner;
    private final UploadedFileRepository uploadedFileRepository;
    private final PostRepository postRepository;

    /**
     * 已知所属帖状态时的判定：**免查库**。仅 APPROVED 公开 → 干净原 URL；其余 → 签名 URL。
     *
     * <p><b>前置条件</b>：调用方必须已确认当前 viewer 有权看到该帖子（详情 / 我的帖子 / 待审队列
     * 都是先做了可见性判定才走到这里），因此本方法恒返回 {@code readable = true}，只负责挑 URL 形态。
     * 新增调用点若跳过可见性判定，会直接把私有图放行——拿不准时改用 {@link #resolve}（按
     * {@code objectKey} 反查并判定可见性）。</p>
     *
     * @param status    图片所属帖子的审核状态；无归属时传 null（按私有处理）
     * @param cleanUrls 干净的对外地址（来自 {@code post_image.url}）
     */
    public List<ImageAccess> forPost(PostStatus status, List<String> cleanUrls) {
        if (cleanUrls == null || cleanUrls.isEmpty()) {
            return List.of();
        }
        if (status == PostStatus.APPROVED) {
            return cleanUrls.stream().map(url -> new ImageAccess(url, true, url)).toList();
        }
        return cleanUrls.stream()
                .map(url -> new ImageAccess(url, true, imageUrlSigner.sign(url)))
                .toList();
    }

    /**
     * 未绑定上传（刚上传、尚未发帖）：仅上传者本人可用，调用方即上传者，故恒签发。
     * 返回的签名 URL 让发帖页预览即刻可取（浏览器 {@code <img>} 不带令牌）。
     */
    public ImageAccess forUpload(String cleanUrl) {
        return new ImageAccess(cleanUrl, true, imageUrlSigner.sign(cleanUrl));
    }

    /**
     * 批量按 {@code objectKey} 反查（草稿恢复等场景）。整批共用一个只读事务，语义与旧
     * {@code ImageUrlAssembler.refresh} 一致。
     */
    @Transactional(readOnly = true)
    public List<ImageAccess> resolveAll(List<String> urls, Long viewerId) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        return urls.stream().map(url -> resolve(url, viewerId)).toList();
    }

    /**
     * 单条按 {@code objectKey} 反查归属，判定当前 viewer 可否读、该给什么 URL。
     *
     * <p>地址非法 / 对象不存在 / 无权访问一律 {@code readable=false} 且 {@code usableUrl=null}
     * （**绝不签发**，避免把无权对象的可用链接发出去）。</p>
     */
    @Transactional(readOnly = true)
    public ImageAccess resolve(String url, Long viewerId) {
        Optional<String> objectKey = StorageUrl.objectKey(url);
        if (objectKey.isEmpty()) {
            // 地址非法：原样回显入参、标记不可用
            return new ImageAccess(url, false, null);
        }
        String cleanUrl = StorageUrl.publicUrl(objectKey.get());
        UploadedFile file = uploadedFileRepository.findByObjectKey(objectKey.get()).orElse(null);
        if (file == null) {
            return new ImageAccess(cleanUrl, false, null);
        }
        if (!file.isBound()) {
            // 未绑定上传：仅上传者本人可用
            return file.isUploadedBy(viewerId)
                    ? new ImageAccess(cleanUrl, true, imageUrlSigner.sign(cleanUrl))
                    : new ImageAccess(cleanUrl, false, null);
        }
        Post post = postRepository.findById(file.getBoundPostId()).orElse(null);
        if (post == null || !post.canBeViewedBy(viewerId)) {
            return new ImageAccess(cleanUrl, false, null);
        }
        String usable = post.getStatus() == PostStatus.APPROVED ? cleanUrl : imageUrlSigner.sign(cleanUrl);
        return new ImageAccess(cleanUrl, true, usable);
    }

    /**
     * 下发授权：携带合法签名的请求**不依赖 viewer 身份**直接放行（签名非法 / 过期一律 false）；
     * 无签名（或只带其一）时回落到 {@link #resolve} 的身份判定。调用方对 false 统一按「不存在」处理。
     */
    @Transactional(readOnly = true)
    public boolean canDeliver(String path, String expRaw, String sigRaw, Long viewerId) {
        if (imageUrlSigner.isSigned(expRaw, sigRaw)) {
            return imageUrlSigner.verify(path, expRaw, sigRaw);
        }
        return resolve(path, viewerId).readable();
    }

    /**
     * 单张上传图的访问判定结果。
     *
     * @param cleanUrl  归一化后的原 URL（去掉签名查询串）；地址非法时原样回显入参
     * @param readable  当前 viewer 是否有权读取
     * @param usableUrl 可直接使用的地址：可读时为原 URL 或签名 URL；不可读时为 null（**绝不签发**）
     */
    public record ImageAccess(String cleanUrl, boolean readable, String usableUrl) {
    }
}
