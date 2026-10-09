-- 青栏 P6：管理端报表 / 学生端热门 新增查询的索引
--
-- 回滚（如需）：
--   ALTER TABLE post   DROP INDEX idx_post_status_created;
--   ALTER TABLE report DROP INDEX idx_report_handled_at;
--   ALTER TABLE report DROP INDEX idx_report_reason;
--
-- 为什么现在加：这些索引不是「提前优化」，而是照着本次新增 Repository 查询的形态反查出来的缺口，
-- 每条都有造数实测的 EXPLAIN 依据。造数规模（本机临时库，测完即删）：
--   report 4 万行（status 80% PENDING / reason 8 档均匀 / created_at 60 天均匀）、
--   post 2 万行（90% APPROVED）、review_log 5 万行。
--
-- EXPLAIN 结论（加索引前 → 加索引后）：
--   Q1 审核队列聚合（status + reason_code IN + target_type，group by target_type,target_id）：
--       ref uk_report_target_reporter，扫 19959 行，Using temporary; Using filesort
--       → 试加 (status,target_type,target_id,reason_code) 复合索引后**优化器仍不选**（同上），
--         故**不加**。聚合本身要临时表 + 排序，现有 idx_report_status_target 已覆盖 status 前导，
--         与本次改造前的队列查询同形，无新增退化。
--   Q2 待处理举报目标数（count distinct target_id）：命中 idx_report_status_target，Using index，
--       无退化，**不加**。
--   Q3 举报按理由分布（group by reason_code）：ALL 全表扫 39919 行 + Using temporary
--       → 加 idx_report_reason 后：type=index，Using index，**无 Using temporary**（索引序即分组序，
--         且为覆盖索引），故**加**。
--   Q4 处置时效样本（status<>PENDING and handled_at >= 窗口）：ALL 全表扫 39940 行
--       → 加 idx_report_handled_at 后：type=range，rows≈4668，故**加**。
--   Q5 热门候选（status=APPROVED and created_at >= 窗口，order by created_at desc,id desc）：
--       ref idx_post_feed，扫 10072 行 + Using filesort
--       → 加 idx_post_status_created 后：type=range，Backward index scan，Using index，**无 filesort**，
--         故**加**。
--   Q6 看板按日处置数 / Q7 审核员排行（review_log created_at 窗口 + action IN）：本次窗口占全表约
--       一半，优化器选 ALL；idx_review_log_created 已在 V5 建立，窄窗口下可命中，规格不变。
--       **不加**。
--   Q8 我的帖子（author_id + status + created_at desc）：命中 idx_post_author(Backward index scan)，
--       无退化，**不加**。
--
-- 执行方式：MySQL 8 的二级索引 ADD INDEX 是 INPLACE 在线 DDL，不锁表、不重建表，可在业务时段执行；
-- 但仍按 AGENTS.md 约定，大表上线放低峰期，并在生产上先 EXPLAIN 复核（本文件结论来自造数环境）。

-- 热门候选：status 前导 + created_at，使「窗口内按 created_at 倒序」免去 filesort
CREATE INDEX idx_post_status_created ON post (status, created_at);

-- 处置时效：已处置举报按 handled_at 窗口取样本（status<>PENDING 非 sargable，故只索引 handled_at）
CREATE INDEX idx_report_handled_at ON report (handled_at);

-- 举报按理由分布：group by reason_code 的覆盖索引（避免临时表）
CREATE INDEX idx_report_reason ON report (reason_code);
