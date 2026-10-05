package com.qinglan.post.repository;

import com.qinglan.post.entity.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PostImageRepository extends JpaRepository<PostImage, Long> {

    List<PostImage> findByPostIdOrderBySortOrderAsc(Long postId);

    /** 列表页按 postId 批量取图，避免逐帖查询造成 N+1 */
    List<PostImage> findByPostIdInOrderBySortOrderAsc(Collection<Long> postIds);
}
