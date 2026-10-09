package com.qinglan.post.service;

import com.qinglan.common.storage.StorageService;
import com.qinglan.common.storage.StorageUrl;
import com.qinglan.post.config.UploadProperties;
import com.qinglan.post.entity.UploadedFile;
import com.qinglan.post.repository.UploadedFileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 孤儿上传回收（T-12' ②）：上传后弃用、或发帖失败而**始终未被绑定**的对象，超过保留期即删除。
 *
 * <p>只删「未绑定且已过保留期」的登记行及其对象。已绑定的（含待审/下架帖的图）**一律保留**——
 * 下架可申诉恢复（不变量 #5），删了图就恢复不了；未过保留期的不动，避免上传后还没发完帖就被回收。</p>
 *
 * <p><b>为什么不写审计日志</b>：不变量 #7 的留痕对象是「下架 / 封禁 / 发布公告」这类**处置动作**，
 * 讲的是「谁对哪条内容做了什么」；孤儿回收是常规存储卫生，没有处置对象、也没有人的决策。
 * 而 {@code review_log} 只增不改删、当前又无归档策略（见 deploy/README.md L4），逐文件写一行会把
 * 一张不清理的表按上传量膨胀。故只落一条**带数量的运行日志**（不含 objectKey 与身份信息）。</p>
 */
@Service
@RequiredArgsConstructor
public class UploadOrphanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(UploadOrphanCleanupService.class);

    /** 单批上限：避免一次把大量对象删除拖成长事务 */
    private static final int BATCH_SIZE = 500;

    private final UploadedFileRepository uploadedFileRepository;
    private final StorageService storageService;
    private final UploadProperties uploadProperties;

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanUpOrphans() {
        if (!uploadProperties.orphanCleanupEnabled()) {
            return;
        }
        Instant cutoff = Instant.now().minus(uploadProperties.orphanRetention());
        List<UploadedFile> orphans = uploadedFileRepository
                .findByBoundPostIdIsNullAndCreatedAtBefore(cutoff, PageRequest.of(0, BATCH_SIZE));
        for (UploadedFile orphan : orphans) {
            // delete 幂等且失败不阻断：对象可能已被手工清理，此时仍应把登记行删掉
            storageService.delete(StorageUrl.publicUrl(orphan.getObjectKey()));
            uploadedFileRepository.delete(orphan);
        }
        if (!orphans.isEmpty()) {
            log.info("孤儿回收：删除超期未绑定的上传 {} 个（保留期 {}）",
                    orphans.size(), uploadProperties.orphanRetention());
        }
    }
}
