package com.qinglan.post.dto;

/**
 * 单个图片地址的换取结果。
 *
 * @param url       归一化后的原 URL（去掉签名查询串）；地址非法时原样回显入参
 * @param available 当前 viewer 是否有权访问；false 表示不可用（前端据此提示「需重新上传」）
 * @param usableUrl 可直接使用的地址：有权访问时为签名 URL 或原 URL；无权访问时为 null（**绝不签发**）
 */
public record ImageUrlRefreshItem(String url, boolean available, String usableUrl) {
}
