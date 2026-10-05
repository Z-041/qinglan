-- 青栏 P0 地基：用户与审计日志
-- 说明：表结构由 Flyway 管理，Hibernate 仅做 validate，不允许自动改表。

CREATE TABLE IF NOT EXISTS `user`
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    email_hash        VARCHAR(64)  NOT NULL COMMENT '校园邮箱不可逆哈希（含 pepper）',
    student_id_enc    VARCHAR(512) NOT NULL COMMENT '学号密文（AES-256-GCM，随机 IV）',
    student_id_hash   VARCHAR(64)  NOT NULL COMMENT '学号不可逆哈希，用于一人一学号唯一约束',
    anonymous_salt    VARCHAR(64)  NOT NULL COMMENT '匿名代号派生盐，一人一值',
    role              VARCHAR(20)  NOT NULL COMMENT 'STUDENT/REVIEWER/ADMIN/SUPER_ADMIN',
    status            VARCHAR(20)  NOT NULL COMMENT 'ACTIVE/BANNED/BLACKLISTED',
    credit_score      INT          NOT NULL DEFAULT 100 COMMENT '信用分，0-100',
    banned_until      DATETIME(6)  NULL COMMENT '限时封禁到期时间（UTC）',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_email_hash (email_hash),
    UNIQUE KEY uk_user_student_id_hash (student_id_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '认证用户';

-- 审计日志：只增不改不删，保留期不少于 6 个月
CREATE TABLE IF NOT EXISTS review_log
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20)  NOT NULL COMMENT 'USER/POST/COMMENT/ANNOUNCEMENT',
    target_id   BIGINT       NOT NULL,
    action      VARCHAR(32)  NOT NULL COMMENT 'BAN/UNBAN/CREDIT_ADJUST/...',
    operator_id BIGINT       NOT NULL COMMENT '操作管理员 userId',
    reason      VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_review_log_target (target_type, target_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '管理动作审计日志';
