package com.qinglan.post.service;

import com.qinglan.common.storage.StorageService;
import com.qinglan.post.config.UploadProperties;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.UploadedFileRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 孤儿回收的行为：删对象 + 删登记行；任务关闭时不做任何事。
 * 过滤条件（未绑定且超保留期）由 {@code UploadedFileRepositoryTest} 在真库上验证。
 */
@ExtendWith(MockitoExtension.class)
class UploadOrphanCleanupServiceTest {

    private static final String KEY = "2026/10/" + "a".repeat(32) + ".jpg";

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private StorageService storageService;

    @Test
    @DisplayName("回收命中的项：删除对象 + 删除登记行")
    void shouldDeleteObjectAndRow() {
        UploadOrphanCleanupService service = service(true, Duration.ofHours(24));
        UploadedFile orphan = UploadedFile.of(7L, KEY);
        when(uploadedFileRepository.findByBoundPostIdIsNullAndCreatedAtBefore(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of(orphan));

        service.cleanUpOrphans();

        verify(storageService).delete("/uploads/" + KEY);
        verify(uploadedFileRepository).delete(orphan);
    }

    @Test
    @DisplayName("任务关闭时不做任何事，不查库也不删对象")
    void shouldSkipWhenDisabled() {
        UploadOrphanCleanupService service = service(false, Duration.ofHours(24));

        service.cleanUpOrphans();

        verifyNoInteractions(uploadedFileRepository, storageService);
    }

    private UploadOrphanCleanupService service(boolean enabled, Duration retention) {
        return new UploadOrphanCleanupService(
                uploadedFileRepository, storageService,
                new UploadProperties(30, Duration.ofMinutes(1), enabled, retention));
    }
}
