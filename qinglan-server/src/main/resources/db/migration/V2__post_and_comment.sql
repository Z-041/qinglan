-- 青栏 P1 发帖闭环：话题、主帖、帖子图片、评论、点赞
--
-- 回滚（如需）：
--   DROP TABLE IF EXISTS like_record, comment, post_image, post, topic;
--
-- 命名说明：设计方案的模型里点赞表叫 `like`，但 LIKE 是 MySQL 8 保留字，
-- 裸写会让所有手写 SQL 与原生查询都必须加反引号，属于长期踩坑点，故命名为 like_record。
-- 表名不加业务前缀的约定不变（like_record 是完整词，不是惰性前缀）。
--
-- 审核状态机见 com.qinglan.post.entity.ReviewState（不变量 #5），status 列存该状态机的持久化名。

-- 话题：预设白名单，不允许自定义（设计方案第三章）
CREATE TABLE IF NOT EXISTS topic
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    name             VARCHAR(20)  NOT NULL COMMENT '话题名',
    rules            VARCHAR(500) NOT NULL COMMENT '该话题的发布规则，发布页展示；无规则话题不允许上线',
    requires_contact BIT(1)       NOT NULL COMMENT '是否要求留联系方式（二手/失物/拼车/兼职为真）',
    enabled          BIT(1)       NOT NULL COMMENT '是否开放发布',
    sort_order       INT          NOT NULL COMMENT '展示顺序',
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '预设话题';

-- 主帖
CREATE TABLE IF NOT EXISTS post
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    author_id          BIGINT        NOT NULL COMMENT '作者 userId',
    topic_id           BIGINT        NOT NULL COMMENT '所属话题',
    content            VARCHAR(2000) NOT NULL COMMENT '正文',
    contact            VARCHAR(200)  NULL COMMENT '联系方式，独立存储以便统一脱敏与批量隐藏（不变量 #2）',
    contact_visibility VARCHAR(20)   NOT NULL COMMENT 'VISIBLE/HIDDEN，HIDDEN 表示被管理员处罚为仅作者可见',
    status             VARCHAR(20)   NOT NULL COMMENT '审核状态持久化名，见 ReviewState',
    anonymous_code     VARCHAR(32)   NOT NULL COMMENT '匿名代号 = hash(post_id + author_id + salt)，跨帖不可关联（不变量 #1）',
    review_reason      VARCHAR(500)  NULL COMMENT '驳回/下架理由',
    review_by          BIGINT        NULL COMMENT '审核人 userId',
    review_at          DATETIME(6)   NULL,
    taken_down_at      DATETIME(6)   NULL,
    report_count       INT           NOT NULL DEFAULT 0 COMMENT '有效举报数（P2 起由举报模块维护）',
    comment_count      INT           NOT NULL DEFAULT 0 COMMENT '可见评论数，由 comment 模块同事务维护；冗余以避免列表 N+1',
    risk_score         INT           NOT NULL DEFAULT 0 COMMENT '机审风险分（P2 起由机审写入）',
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- 列表按 id 倒序游标翻页，过滤 status；把 status 放前导列可命中
    KEY idx_post_feed (status, id),
    KEY idx_post_topic_feed (topic_id, status, id),
    KEY idx_post_author (author_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '主帖';

-- 帖子图片：正文与图片分离，便于二期单独补图像审核
CREATE TABLE IF NOT EXISTS post_image
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    post_id           BIGINT       NOT NULL,
    url               VARCHAR(500) NOT NULL COMMENT '原图访问地址',
    thumbnail_url     VARCHAR(500) NULL COMMENT '缩略图地址',
    sensitive_checked BIT(1)       NOT NULL COMMENT '一期不做图像识别，恒为 false，字段保留给二期',
    sort_order        INT          NOT NULL COMMENT '九宫格展示顺序',
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_post_image_post (post_id, sort_order)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '帖子图片';

-- 二级评论：先发后审，不进审核状态机
CREATE TABLE IF NOT EXISTS comment
(
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    post_id        BIGINT       NOT NULL,
    author_id      BIGINT       NOT NULL,
    parent_id      BIGINT       NULL COMMENT '为空即一级评论；非空即对一级评论的回复',
    content        VARCHAR(500) NOT NULL,
    status         VARCHAR(20)  NOT NULL COMMENT 'PUBLISHED/REMOVED',
    anonymous_code VARCHAR(32)  NOT NULL COMMENT '同样按帖子派生，跨帖不可关联（不变量 #1）',
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comment_post (post_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '二级评论';

-- 点赞：多态目标 + 联合唯一，天然幂等
CREATE TABLE IF NOT EXISTS like_record
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    target_type VARCHAR(20) NOT NULL COMMENT 'POST/COMMENT',
    target_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_like_target_user (target_type, target_id, user_id),
    KEY idx_like_user (user_id, target_type, target_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '点赞';

-- 预设话题种子（设计方案第三章：表白 / 失物招领 / 二手 / 拼车 / 问答 / 吐槽 / 兼职）
INSERT INTO topic (name, rules, requires_contact, enabled, sort_order, created_at, updated_at)
VALUES ('表白', '请勿公开他人姓名、学号、宿舍号等可识别信息；禁止以表白之名隔空挂人。', b'0', b'1', 1, NOW(6), NOW(6)),
       ('失物招领', '请写明物品特征、丢失或拾获地点与时间；捡到他人物品请尽快交至失物招领处。', b'1', b'1', 2, NOW(6), NOW(6)),
       ('二手', '请注明价格、成色与交易方式；建议当面验货，谨防先款后货诈骗。', b'1', b'1', 3, NOW(6), NOW(6)),
       ('拼车', '请写明出发地、目的地、时间与空位；请勿发布营运性质的载客信息。', b'1', b'1', 4, NOW(6), NOW(6)),
       ('问答', '请把问题描述清楚并说明已尝试的做法；回答请就事论事。', b'0', b'1', 5, NOW(6), NOW(6)),
       ('吐槽', '对事不对人；禁止曝光他人隐私、人肉搜索与人身攻击。', b'0', b'1', 6, NOW(6), NOW(6)),
       ('兼职', '请注明岗位、时间与报酬；警惕押金、刷单、代收快递等诈骗套路。', b'1', b'1', 7, NOW(6), NOW(6));
