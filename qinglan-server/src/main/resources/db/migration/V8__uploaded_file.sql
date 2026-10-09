-- 青栏 T-12'：上传文件登记（图片归属绑定 + 孤儿回收）
--
-- 回滚（如需）：
--   DROP TABLE IF EXISTS uploaded_file;
-- 本迁移只新增一张表 + 一次性回填，不改任何既有表结构；回滚即删表，旧代码忽略它仍可运行
-- （旧代码不引用 uploaded_file，见 deploy/README.md 的 expand-contract 约定）。
--
-- 为什么需要这张表：上传接口只把文件写进存储、不落库，发帖时才把这些地址写进 post_image。
-- 于是有两个缺口（T-12'）：
--   ① 归属未绑定——任何人都能复制他人图片 URL 发帖（盗链）；
--   ② 上传后弃用/发帖失败的文件永久留存且可公开访问（孤儿）。
-- 本表在上传时就登记一行（uploader_id + object_key），发帖时把 bound_post_id 填上；
-- 图片下发端点据此反查「该对象属于谁、绑给了哪个帖子」，从而按帖子可见性裁决（T-27①）。
--
-- 索引说明（对象下载是最热路径之一，逐条给出必要性）：
--   uk_uploaded_file_object_key：object_key 唯一。它既是**正确性约束**（同一对象不允许两条登记，
--     否则绑定关系二义），也是图片下发按 key 反查的入口（点查，const）。
--   idx_uploaded_file_gc (bound_post_id, created_at)：回收任务按
--     `WHERE bound_post_id IS NULL AND created_at < 截止时间` 取过期未绑定项。
--     EXPLAIN 依据（本机 MySQL 8，造数 3 万行、其中 1 万行未绑定，测完即删）：
--       加索引前：type=ALL，rows=30030，filtered=3.33，Extra=Using where
--       加索引后：type=range，key=idx_uploaded_file_gc，rows=14634，filtered=100，Extra=Using where; Using index
--     故加。`bound_post_id IS NULL` 被 InnoDB 当作索引范围使用，且该索引覆盖查询所需列。
--
-- 回填历史数据：把已被帖子引用的图片补登记为「已绑定」，使存量的已发布图片在新端点下
-- 仍按所属帖子可见性下发。用派生表 GROUP BY object_key 去重——历史上（本轮收紧之前）
-- 同一 URL 被多个帖子引用是可能的，直接 INSERT 会撞唯一键导致迁移失败；
-- 去重时保留最小 post_id 作为绑定目标（多帖引用同一 URL 属历史遗留的盗链，本轮起不再可能）。

CREATE TABLE IF NOT EXISTS uploaded_file
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    uploader_id   BIGINT       NOT NULL COMMENT '上传者 userId；未被任何帖子引用时仅其本人可见',
    object_key    VARCHAR(255) NOT NULL COMMENT '对象 key = 对外 URL 去掉 /uploads/ 前缀后的路径段',
    bound_post_id BIGINT       NULL COMMENT '已绑定的帖子 id；为空表示尚未被任何帖子引用',
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_uploaded_file_object_key (object_key),
    KEY idx_uploaded_file_gc (bound_post_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '上传文件登记：归属绑定与孤儿回收';

INSERT INTO uploaded_file (uploader_id, object_key, bound_post_id, created_at, updated_at)
SELECT p.author_id, d.object_key, d.post_id, d.created_at, d.updated_at
FROM (SELECT SUBSTRING(pi.url, LENGTH('/uploads/') + 1) AS object_key,
             MIN(pi.post_id)                              AS post_id,
             MIN(pi.created_at)                           AS created_at,
             MAX(pi.updated_at)                           AS updated_at
      FROM post_image pi
      WHERE pi.url LIKE '/uploads/%'
      GROUP BY SUBSTRING(pi.url, LENGTH('/uploads/') + 1)) d
         JOIN post p ON p.id = d.post_id;
