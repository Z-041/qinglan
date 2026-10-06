-- 青栏 P4 公告与个人通知：公告、公告已读、个人通知
--
-- 回滚（如需）：
--   DROP TABLE IF EXISTS notification, announcement_read, announcement;
--
-- 偏离说明（计划 D4）：设计方案 5.6 原写「cookie 下发 device_id」，但本项目 SecurityConfig 明确不使用
-- Cookie/Session（无 CORS、CSRF 已 disable），引入 Cookie 会牵动 CSRF 与跨域策略。故改为前端生成并
-- 持久化 device_id（localStorage）后随请求回传，效果等价且不动安全配置。游客已读记录落 device_id 列。
--
-- 特殊约束说明：
--   announcement_read 同时按 user_id 与 device_id 建唯一键，登录用户与游客都不重复弹（计划 D5）。
--   两列皆可空——MySQL 唯一索引对 NULL 不去重，故「游客只填 device_id、登录用户只填 user_id」两个场景
--   各自的唯一性由对应唯一键保证，互不干扰。
--   notification 的正文用 title/body/link 三列冗余快照存储，通知不随源对象后续变化（设计方案第七章）。

-- 公告：强制阅读与更正走 supersedes_id，撤回不删已读记录（设计方案 5.1）
CREATE TABLE IF NOT EXISTS announcement
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    title                 VARCHAR(120) NOT NULL COMMENT '标题',
    content               TEXT         NOT NULL COMMENT '正文',
    level                 VARCHAR(20)  NOT NULL COMMENT 'URGENT/NORMAL/ACTIVITY',
    status                VARCHAR(20)  NOT NULL COMMENT 'DRAFT/PUBLISHED/REVOKED',
    start_at              DATETIME(6)  NOT NULL COMMENT '生效开始',
    end_at                DATETIME(6)  NULL COMMENT '生效结束；NULL 表示不过期',
    min_read_seconds      INT          NOT NULL COMMENT '强制阅读最少秒数',
    require_scroll_to_end BIT(1)       NOT NULL COMMENT '是否要求滚动到底',
    require_confirm       BIT(1)       NOT NULL COMMENT '是否要求勾选已了解',
    supersedes_id         BIGINT       NULL COMMENT '更正前一条公告的 id',
    created_by            BIGINT       NOT NULL COMMENT '创建者 userId',
    published_at          DATETIME(6)  NULL COMMENT '发布时间',
    revoked_at            DATETIME(6)  NULL COMMENT '撤回时间',
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    -- 待读查一条：status + level 优先级 + published_at 倒序
    KEY idx_announcement_pending (status, level, published_at),
    -- 历史 / 管理端列表：status + published_at
    KEY idx_announcement_history (status, published_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '公告';

-- 公告已读：强制阅读的证据（不变量 #6），同时按用户与设备去重
CREATE TABLE IF NOT EXISTS announcement_read
(
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    announcement_id    BIGINT      NOT NULL,
    user_id            BIGINT      NULL COMMENT '登录用户；游客为 NULL',
    device_id          VARCHAR(64) NULL COMMENT '游客设备标识（前端生成并持久化）',
    read_at            DATETIME(6) NOT NULL COMMENT '标记已读时刻',
    read_duration_ms   BIGINT      NOT NULL COMMENT '真实停留时长（毫秒），强制阅读的证据',
    confirmed          BIT(1)      NOT NULL COMMENT '是否勾选已了解',
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_announcement_read_user (announcement_id, user_id),
    UNIQUE KEY uk_announcement_read_device (announcement_id, device_id),
    KEY idx_announcement_read_user (user_id, announcement_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '公告已读记录';

-- 个人通知：内容为快照，不随源对象变化；不得含作者身份字段（不变量 #1 的引申）
CREATE TABLE IF NOT EXISTS notification
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL COMMENT '接收者',
    type       VARCHAR(32)  NOT NULL COMMENT 'COMMENT/LIKE/REVIEW_RESULT/REPORT_FEEDBACK',
    title      VARCHAR(120) NOT NULL COMMENT '标题（冗余存储，通知不随源对象变化）',
    body       VARCHAR(500) NULL COMMENT '正文摘要',
    link       VARCHAR(200) NULL COMMENT '前端跳转路径',
    read_at    DATETIME(6)  NULL COMMENT '已读时刻；NULL 表示未读',
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_user (user_id, id),
    KEY idx_notification_unread (user_id, read_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '个人通知';
