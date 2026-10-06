-- 青栏 P2 审核体系：举报与申诉
--
-- 回滚（如需）：
--   DROP TABLE IF EXISTS appeal, report;
--
-- 复用既有列（V2 已建，无需新增）：
--   post.report_count —— 待处理举报数，由举报模块同事务原子维护（与 comment_count 同为冗余计数）
--   post.review_by    —— 原处置人，申诉复审时校验「复审人 ≠ 原处置人」
--
-- 状态取值见 com.qinglan.review.entity 下的枚举，列存枚举持久化名。

-- 举报：多态目标 + 联合唯一，同一用户对同一目标只能举报一次（不变量 #4）
CREATE TABLE IF NOT EXISTS report
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20)  NOT NULL COMMENT 'POST/COMMENT',
    target_id   BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL COMMENT '举报人 userId',
    reason_code VARCHAR(32)  NOT NULL COMMENT '举报理由，与下架违规类型共用 ReportReason',
    detail      VARCHAR(500) NULL COMMENT '补充说明，可空',
    status      VARCHAR(20)  NOT NULL COMMENT 'PENDING/UPHELD/DISMISSED',
    handled_by  BIGINT       NULL COMMENT '处置人 userId；未处置时为空',
    handled_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- 不变量 #4：重复举报由数据库兜底拒绝，业务层映射为 409 而非静默吞掉
    UNIQUE KEY uk_report_target_reporter (target_type, target_id, reporter_id),
    -- 审核队列按目标聚合待处理举报，命中前导三列
    KEY idx_report_target_status (target_type, target_id, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '举报';

-- 申诉：作者对被下架的内容申请复审
CREATE TABLE IF NOT EXISTS appeal
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    target_type  VARCHAR(20)  NOT NULL COMMENT 'POST/COMMENT',
    target_id    BIGINT       NOT NULL,
    appellant_id BIGINT       NOT NULL COMMENT '申诉人 userId，必须是被处置内容的作者',
    reason       VARCHAR(500) NOT NULL COMMENT '申诉理由',
    status       VARCHAR(20)  NOT NULL COMMENT 'PENDING/ACCEPTED/REJECTED',
    reviewed_by  BIGINT       NULL COMMENT '复审人 userId，必须 ≠ 原处置人 post.review_by',
    reviewed_at  DATETIME(6)  NULL,
    restored     BIT(1)       NOT NULL DEFAULT b'0' COMMENT '复审通过后内容是否已恢复',
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- 每个目标只受理一次申诉：反复申诉会无底线消耗审核资源，限一次
    UNIQUE KEY uk_appeal_target (target_type, target_id),
    KEY idx_appeal_status (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '申诉';
