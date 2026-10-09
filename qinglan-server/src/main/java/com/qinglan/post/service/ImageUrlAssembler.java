package com.qinglan.post.service;

import com.qinglan.common.storage.ImageUrlSigner;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.post.dto.ImageUrlRefreshItem;
import com.qinglan.post.entity.Post;
import com.qinglan.post.entity.PostStatus;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.PostRepository;
import com.qinglan.post.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 图片地址的对外装配：按「所属帖子状态 + 当前 viewer」决定给出原 URL 还是短时签名 URL。
 *
 * <p><b>为什么集中在这里</b>：详情、我的帖子、上传返回、批量换取四处都要做同一个判断，各写一份必然漂移。
 * 规则只有一条——<b>所属帖子为 APPROVED（对所有人公开）→ 原 URL（保持干净、可缓存）；其余（PENDING /
 * REJECTED / TAKEN_DOWN，以及未绑定上传）→ 签名 URL</b>。</p>
 *
 * <p><b>不在只读场景额外查库</b>：帖子状态在装配处（详情 / 我的帖子）已随实体持有，直接传入状态判断；
 * 只有批量换取端点才需要按 URL 反查归属（那正是它的职责）。</p>
 */
@Component
@RequiredArgsConstructor
public class ImageUrlAssembler {

    private final ImageUrlSigner imageUrlSigner;
    private final UploadedFileRepository uploadedFileRepository;
    private final PostRepository postRepository;

    /**
     * 按帖子状态装配图片地址。APPROVED 原样返回，其余签名为短时 URL。
     *
     * @param status    图片所属帖子的审核状态
     * @param imageUrls 干净的对外地址（{@code /uploads/...}，来自 {@code post_image.url}）
     */
    public List<String> forPost(PostStatus status, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return List.of();
        }
        if (status == PostStatus.APPROVED) {
            return imageUrls;
        }
        return imageUrls.stream().map(imageUrlSigner::sign).toList();
    }

    /**
     * 批量换取当前 viewer 可直接使用的地址（草稿恢复等场景）。
     *
     * <p>逐个判定：有权访问 → 签名 URL（私有）或原 URL（已通过帖）；无权访问 / 地址非法 / 对象不存在
     * → {@code available=false} 且 {@code usableUrl=null}（**绝不签发**，避免把无权对象的可用链接发出去）。</p>
     */
    @Transactional(readOnly = true)
    public List<ImageUrlRefreshItem> refresh(List<String> urls, Long viewerId) {
        if (urls == null || urls.isEmpty()) {
            return List.of();
        }
        List<ImageUrlRefreshItem> items = new ArrayList<>(urls.size());
        for (String url : urls) {
            items.add(refreshOne(url, viewerId));
        }
        return items;
    }

    private ImageUrlRefreshItem refreshOne(String url, Long viewerId) {
        Optional<String> objectKey = StorageUrl.objectKey(url);
        if (objectKey.isEmpty()) {
            // 地址非法：原样回显、标记不可用（绝不签发）
            return new ImageUrlRefreshItem(url, false, null);
        }
        String cleanUrl = StorageUrl.publicUrl(objectKey.get());
        UploadedFile file = uploadedFileRepository.findByObjectKey(objectKey.get()).orElse(null);
        if (file == null) {
            return new ImageUrlRefreshItem(cleanUrl, false, null);
        }
        if (!file.isBound()) {
            // 未绑定上传：仅上传者本人可用
            return file.isUploadedBy(viewerId)
                    ? new ImageUrlRefreshItem(cleanUrl, true, imageUrlSigner.sign(cleanUrl))
                    : new ImageUrlRefreshItem(cleanUrl, false, null);
        }
        Post post = postRepository.findById(file.getBoundPostId()).orElse(null);
        if (post == null || !post.canBeViewedBy(viewerId)) {
            return new ImageUrlRefreshItem(cleanUrl, false, null);
        }
        String usable = post.getStatus() == PostStatus.APPROVED ? cleanUrl : imageUrlSigner.sign(cleanUrl);
        return new ImageUrlRefreshItem(cleanUrl, true, usable);
    }
}
