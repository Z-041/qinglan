package com.qinglan.post.service;

import com.qinglan.post.dto.TopicResponse;
import com.qinglan.post.entity.Topic;
import com.qinglan.post.repository.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 话题字典。话题由 Flyway 种入，运行期只读（设计方案第三章：预设，不可自定义）。
 */
@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public List<TopicResponse> listEnabled() {
        return topicRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(TopicService::toResponse)
                .toList();
    }

    private static TopicResponse toResponse(Topic topic) {
        return new TopicResponse(topic.getId(), topic.getName(), topic.getRules(), topic.isRequiresContact());
    }
}
