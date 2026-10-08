-- 青栏 P5 二期：处置表决（提案 + 投票）
--
-- 回滚（如需）：
--   DROP TABLE IF EXISTS review_vote, review_proposal;
--
-- 背景（P5 决策 D5 / D6 / D7）：对**已发布内容**的处置不再由单人决定——先提案，委员会严格过半同意
-- 才执行；一旦「同意票已不可能过半」立即上报超管裁决。待审帖子的通过/驳回与驳回举报仍是单人动作。
--
-- 两张表都只增不改删（与 review_log 同性质），因此实体上没有 setter，仓储也不继承 JpaRepository。
-- 投票**不写进 review_log**：那里是「治理动作」的留痕，投票不该淹没它（方案 6.2 的具体决定）。

-- 提案：一条「对某目标执行某处置」的申请
CREATE TABLE IF NOT EXISTS review_proposal
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    target_type     VARCHAR(20)  NOT NULL COMMENT 'POST / COMMENT',
    target_id       BIGINT       NOT NULL,
    action          VARCHAR(32)  NOT NULL COMMENT 'TAKE_DOWN / REMOVE_COMMENT / HIDE_CONTACT / UPHOLD_TAKEDOWN',
    reason_code     VARCHAR(32)  NOT NULL COMMENT '违规类型码，复用车处置理由枚举',
    detail          VARCHAR(500) NULL COMMENT '补充说明',
    proposer_id     BIGINT       NOT NULL COMMENT '发起人 userId',
    snapshot_size   INT          NOT NULL COMMENT '创建时的在册委员会人数（表决分母快照，方案 D6）',
    status          VARCHAR(20)  NOT NULL COMMENT 'OPEN / APPROVED / REJECTED / ESCALATED',
    resolution      VARCHAR(200) NULL COMMENT '结论说明：表决通过（4/6 同意）/ 超管裁决 / 执行失败原因',
    resolved_by     BIGINT       NULL COMMENT '结案人：超管裁决时为其 userId，表决执行时为发起人（见方案 15.x 偏差）',
    resolved_at     DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    -- 「同一目标同时只允许一个进行中提案」的落点：MySQL 8 没有部分唯一索引，故用一个可空列承载
    -- 目标键（OPEN 时写 'POST:7'，结案时置 NULL），唯一键建在它上面——NULL 不参与唯一性判断。
    -- 用 VARCHAR 而非 CHAR：实体的 @Column(length = 40) 映射为 varchar，CHAR 会让 ddl-auto=validate 报列类型不符。
    open_target_key VARCHAR(40)  NULL COMMENT '进行中提案的目标键；结案后置 NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_proposal_open_target (open_target_key),
    KEY idx_proposal_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='处置提案（委员会表决）';

-- 票：一人一票，只有同意 / 不同意（弃权＝不投，计入分母；方案 5.2）
CREATE TABLE IF NOT EXISTS review_vote
(
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    proposal_id BIGINT      NOT NULL,
    voter_id    BIGINT      NOT NULL,
    decision    VARCHAR(10) NOT NULL COMMENT 'AGREE / DISAGREE',
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vote_proposal_voter (proposal_id, voter_id),
    KEY idx_vote_proposal (proposal_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='表决投票';
