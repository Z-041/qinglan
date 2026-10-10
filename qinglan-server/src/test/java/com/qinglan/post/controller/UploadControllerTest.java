package com.qinglan.post.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.dto.UploadedImageResponse;
import com.qinglan.post.service.ImageAccessService;
import com.qinglan.post.service.ImageUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图片上传与地址换取接口的参数校验与状态码映射（Web 层切片）。
 *
 * <p>两个端点均需登录；类型 / 大小 / 归属判定在 Service 层，此处只校验 401、DTO 约束的 400
 * 与成功路径的响应装配（含上传者取自登录态、批量换取结果逐条回填）。</p>
 */
@WebMvcTest(controllers = UploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class UploadControllerTest {

    private static final long USER_ID = 7L;
    private static final String IMAGE_PATH = "/uploads/2026/10/0123456789abcdef0123456789abcdef.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageUploadService imageUploadService;

    @MockitoBean
    private ImageAccessService imageAccessService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(USER_ID, null, List.of()));
    }

    private static MockMultipartFile pngFile() {
        return new MockMultipartFile("file", "photo.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("上传图片成功，上传者取自登录态")
    void shouldUploadImage() throws Exception {
        authenticate();
        when(imageUploadService.upload(eq(USER_ID), any()))
                .thenReturn(new UploadedImageResponse(IMAGE_PATH + "?exp=1&sig=abc"));

        mockMvc.perform(multipart("/api/uploads/images").file(pngFile()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data.url").value(IMAGE_PATH + "?exp=1&sig=abc"));

        verify(imageUploadService).upload(eq(USER_ID), any());
    }

    @Test
    @DisplayName("未登录上传图片返回 401")
    void shouldRejectAnonymousUpload() throws Exception {
        mockMvc.perform(multipart("/api/uploads/images").file(pngFile()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }

    @Test
    @DisplayName("上传被限流：服务层抛 429 原样返回")
    void shouldPropagateUploadRateLimit() throws Exception {
        authenticate();
        doThrow(new BizException(ErrorCode.UPLOAD_TOO_FREQUENT)).when(imageUploadService).upload(eq(USER_ID), any());

        mockMvc.perform(multipart("/api/uploads/images").file(pngFile()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QL-429-003"));
    }

    @Test
    @DisplayName("完全不带 file 部分返回 400（而不是落到兜底分支的 500）")
    void shouldRejectMissingFilePart() throws Exception {
        authenticate();

        mockMvc.perform(multipart("/api/uploads/images"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"));
    }

    @Test
    @DisplayName("批量换取图片地址成功，逐条回填可读与可用地址")
    void shouldRefreshImageUrls() throws Exception {
        authenticate();
        when(imageAccessService.resolveAll(anyList(), eq(USER_ID))).thenReturn(List.of(
                new ImageAccessService.ImageAccess(IMAGE_PATH, true, IMAGE_PATH),
                new ImageAccessService.ImageAccess(IMAGE_PATH, false, null)));

        mockMvc.perform(post("/api/uploads/image-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"" + IMAGE_PATH + "\",\"" + IMAGE_PATH + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("QL-000-000"))
                .andExpect(jsonPath("$.data[0].available").value(true))
                .andExpect(jsonPath("$.data[0].usableUrl").value(IMAGE_PATH))
                .andExpect(jsonPath("$.data[1].available").value(false))
                .andExpect(jsonPath("$.data[1].usableUrl").value(org.hamcrest.Matchers.nullValue()));

        verify(imageAccessService).resolveAll(anyList(), eq(USER_ID));
    }

    @Test
    @DisplayName("图片地址列表为空返回 400")
    void shouldRejectEmptyUrlList() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/uploads/image-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QL-400-001"))
                .andExpect(jsonPath("$.message").value("请提供要换取的图片地址"));
    }

    @Test
    @DisplayName("未登录换取图片地址返回 401")
    void shouldRejectAnonymousRefresh() throws Exception {
        mockMvc.perform(post("/api/uploads/image-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"urls\":[\"" + IMAGE_PATH + "\"]}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("QL-401-001"));
    }
}
