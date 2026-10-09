package com.qinglan.post.repository;

import com.qinglan.post.entity.UploadedFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {

    /** 图片下发与发帖校验的入口：按对象 key 点查（命中 uk_uploaded_file_object_key） */
    Optional<UploadedFile> findByObjectKey(String objectKey);

    /**
     * 回收候选：截止时间前上传、且始终未被任何帖子绑定的登记行。
     *
     * <p>用 {@link Pageable} 限制单批条数，避免一次回收把大量对象删除拖成长事务。</p>
     */
    List<UploadedFile> findByBoundPostIdIsNullAndCreatedAtBefore(Instant cutoff, Pageable pageable);
}
