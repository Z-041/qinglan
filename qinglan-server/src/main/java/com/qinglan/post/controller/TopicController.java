package com.qinglan.post.controller;

import com.qinglan.common.web.ApiResponse;
import com.qinglan.post.dto.TopicResponse;
import com.qinglan.post.service.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 话题字典接口，游客亦可读取（发布页与首页筛选都要用）。
 */
@RestController
@RequestMapping("/api/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public ApiResponse<List<TopicResponse>> list() {
        return ApiResponse.ok(topicService.listEnabled());
    }
}
