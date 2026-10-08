package com.qinglan.review.dto;

/**
 * 发起提案的结果：只回提案号，发起人随后自行投票（方案 7.4）。
 */
public record CreateProposalResponse(Long proposalId) {
}
