package com.qinglan.post.controller;

import com.qinglan.auth.security.JwtService;
import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.post.service.ImageDeliveryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 受控图片下发端点（{@code GET /uploads/**}，Web 层切片）。
 *
 * <p>可见性与签名校验在 {@code ImageDeliveryService}/{@code ImageAccessService} 中，这里只校验
 * 成功下发的响应头装配（Content-Type / ETag / 短 TTL 私有缓存）与「不可见即 404」的映射。</p>
 */
@WebMvcTest(controllers = UploadedImageController.class)
@AutoConfigureMockMvc(addFilters = false)
class UploadedImageControllerTest {

    private static final String IMAGE_PATH = "/uploads/2026/10/0123456789abcdef0123456789abcdef.png";
    private static final byte[] IMAGE_BYTES = {1, 2, 3, 4};

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageDeliveryService imageDeliveryService;

    @MockitoBean
    private JwtService jwtService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("下发图片返回 200，带 Content-Type、ETag 与私有短缓存")
    void shouldServeImage() throws Exception {
        when(imageDeliveryService.load(eq(IMAGE_PATH), isNull(), isNull(), isNull()))
                .thenReturn(new ImageDeliveryService.DeliveredImage(
                        IMAGE_BYTES, "image/png", Instant.parse("2026-10-07T00:00:00Z"), "etag-1"));

        mockMvc.perform(get(IMAGE_PATH))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(IMAGE_BYTES))
                .andExpect(header().string(HttpHeaders.ETAG, "\"etag-1\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("private")));
    }

    @Test
    @DisplayName("携带签名查询串时原样透传给服务层，游客 viewer 为空")
    void shouldPassSignatureParams() throws Exception {
        when(imageDeliveryService.load(eq(IMAGE_PATH), eq("123"), eq("sigvalue"), isNull()))
                .thenReturn(new ImageDeliveryService.DeliveredImage(
                        IMAGE_BYTES, "image/png", null, null));

        mockMvc.perform(get(IMAGE_PATH).param("exp", "123").param("sig", "sigvalue"))
                .andExpect(status().isOk());

        verify(imageDeliveryService).load(eq(IMAGE_PATH), eq("123"), eq("sigvalue"), isNull());
    }

    @Test
    @DisplayName("已登录访问时把当前用户 id 传给服务层")
    void shouldPassViewerIdWhenAuthenticated() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(7L, null, List.of()));
        when(imageDeliveryService.load(eq(IMAGE_PATH), isNull(), isNull(), eq(7L)))
                .thenReturn(new ImageDeliveryService.DeliveredImage(
                        IMAGE_BYTES, "image/png", null, null));

        mockMvc.perform(get(IMAGE_PATH))
                .andExpect(status().isOk());

        verify(imageDeliveryService).load(eq(IMAGE_PATH), isNull(), isNull(), eq(7L));
    }

    @Test
    @DisplayName("图片不可见或不存在一律返回 404，不泄露存在性")
    void shouldReturn404WhenNotDeliverable() throws Exception {
        when(imageDeliveryService.load(any(), any(), any(), any()))
                .thenThrow(new BizException(ErrorCode.IMAGE_NOT_FOUND));

        mockMvc.perform(get(IMAGE_PATH))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QL-404-010"));
    }
}
