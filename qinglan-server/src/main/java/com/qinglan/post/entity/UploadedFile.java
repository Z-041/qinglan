package com.qinglan.post.entity;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 上传文件登记（T-12'）。
 *
 * <p>上传接口落一行、发帖时回填 {@code boundPostId}。两个用途：</p>
 * <ul>
 *   <li><b>归属绑定</b>：发帖只能引用 {@code uploaderId} 是本人、且 {@code boundPostId} 为空的对象</li>
 *   <li><b>可见性/回收</b>：图片下发按 {@code objectKey} 反查到本行，未绑定时仅上传者可见；
 *       长期未绑定的行由回收任务删除（对象 + 本行）</li>
 * </ul>
 *
 * <p>不提供 setter：绑定只能经 {@link #bindTo(Long)}，避免绕过「一个对象只绑一个帖子」的约束。</p>
 */
@Getter
@Entity
@Table(name = "uploaded_file",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_uploaded_file_object_key", columnNames = "object_key"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadedFile extends BaseEntity {

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    /** 对象 key = 对外 URL 去掉 {@code /uploads/} 前缀后的路径段 */
    @Column(name = "object_key", nullable = false, length = 255)
    private String objectKey;

    /** 已绑定的帖子 id；为空表示尚未被任何帖子引用 */
    @Column(name = "bound_post_id")
    private Long boundPostId;

    public static UploadedFile of(Long uploaderId, String objectKey) {
        UploadedFile file = new UploadedFile();
        file.uploaderId = uploaderId;
        file.objectKey = objectKey;
        return file;
    }

    public boolean isBound() {
        return boundPostId != null;
    }

    public boolean isUploadedBy(Long userId) {
        return userId != null && userId.equals(this.uploaderId);
    }

    /**
     * 绑定到帖子。已绑定过则拒绝——同一对象不允许被两个帖子引用，否则「下架帖的图」归属二义。
     */
    public void bindTo(Long postId) {
        if (isBound()) {
            throw new BizException(ErrorCode.UPLOAD_ALREADY_BOUND);
        }
        this.boundPostId = postId;
    }
}
