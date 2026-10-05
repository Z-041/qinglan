package com.qinglan.post.entity;

import com.qinglan.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 帖子图片。与正文分离存储，便于二期单独补图像审核链路。
 *
 * <p>一期不做图像识别（设计方案第四章），故 {@code sensitiveChecked} 恒为 false。</p>
 */
@Getter
@Entity
@Table(name = "post_image")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImage extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    /** 缩略图地址，P1 未做转码，暂为空；前端使用原图地址 */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "sensitive_checked", nullable = false)
    private boolean sensitiveChecked;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static PostImage of(Long postId, String url, int sortOrder) {
        PostImage image = new PostImage();
        image.postId = postId;
        image.url = url;
        image.sensitiveChecked = false;
        image.sortOrder = sortOrder;
        return image;
    }
}
