-- 青栏 P4 收尾：补齐高频查询缺失的索引（R-31）
--
-- 回滚（如需）：
--   ALTER TABLE review_log DROP INDEX idx_review_log_created;
--   ALTER TABLE post       DROP INDEX idx_post_pending;
--   ALTER TABLE comment    DROP INDEX idx_comment_parent;
--   ALTER TABLE report     DROP INDEX idx_report_status_target;
--   ALTER TABLE report     DROP INDEX idx_report_reporter;
--   ALTER TABLE `user`     DROP INDEX idx_user_created;
--
-- 为什么现在加：这些索引不是「提前优化」，而是照着现有 Repository 的查询形态反查出来的缺口。
-- 造数实测（review_log 5 万 / post 2 万 / comment 3 万 / report 3 万 / user 5 千）的 EXPLAIN：
--   Q1 看板处置数（每次加载 8 次）：type=index，扫 49880 行        → 加索引后 type=range
--   Q2 待审帖子队列：走了 id 序索引 + Using filesort              → 改为 status 前导后无排序（单状态）
--   Q3 评论回复列表：type=ALL 全表扫 30010 行 + Using filesort    → type=range，rows=3
--   Q4 审核队列聚合：type=index 扫 30051 行                       → type=ref
--   Q5 用户管理列表：type=ALL 4892 行 + Using filesort            → Backward index scan，rows=21
--   Q6 同一举报人的其他举报：扫 uk 索引 30051 行                  → type=range，rows=3
-- 说明：Q2 在 status IN (PENDING, REVIEWING) 时优化器仍可能选 idx_post_feed（IN 多值的代价模型
-- 所致，数据里 REVIEWING 为 0 行属极端分布）；单状态时确认走 idx_post_pending 且无 filesort。
--
-- 执行方式：MySQL 8 的二级索引 ADD INDEX 是 INPLACE 在线 DDL，不锁表、不重建表，可在业务时段执行；
-- 但仍按 AGENTS.md 约定，大表上线放低峰期，并在生产上先 EXPLAIN 复核（本文件的结论来自造数环境）。

-- 看板：按 created_at 范围做 8 次 count（今日 + 近 7 日）
CREATE INDEX idx_review_log_created ON review_log (created_at);

-- 待审帖子队列：status in (PENDING, REVIEWING) + order by created_at asc, id asc
CREATE INDEX idx_post_pending ON post (status, created_at, id);

-- 评论回复：parent_id in (...) + status + order by created_at asc
CREATE INDEX idx_comment_parent ON comment (parent_id, created_at);

-- 审核队列聚合：status 前导（原索引是 target_type 前导，纯 status 条件用不上）
CREATE INDEX idx_report_status_target ON report (status, target_type, target_id);

-- 上下文面板「同一举报人是否重复举报」：按 reporter_id 反查
CREATE INDEX idx_report_reporter ON report (reporter_id);

-- 用户管理列表：order by created_at desc, id desc
CREATE INDEX idx_user_created ON `user` (created_at, id);
