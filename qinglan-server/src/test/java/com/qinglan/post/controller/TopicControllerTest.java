package com.qinglan.post.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.post.dto.TopicResponse;
import com.qinglan.post.service.TopicService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 话题字典接口（Web 层切片）。游客亦可读，无参数校验与鉴权分支，只校验成功路径装配。
 */
@WebMvcTest(controllers = TopicController.class)
@AutoConfigureMockMvc(addFilters = false)
class TopicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TopicService topicService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    @DisplayName("列出启用话题返回 200 与统一成功码")
    void shouldListEnabledTopics() throws Exception {
        when(topicService.listEnabled()).thenReturn(List.of(
                new TopicResponse(3L, "树洞", "请勿人身攻击", false),
                new TopicResponse(4L, "失物招领", "请留下联系方式", true)));

        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(3))
                .andExpect(jsonPath("$.data[1].requiresContact").value(true));
    }

    @Test
    @DisplayName("无启用话题时返回空数组而非 null")
    void shouldReturnEmptyList() throws Exception {
        when(topicService.listEnabled()).thenReturn(List.of());

        mockMvc.perform(get("/api/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}
