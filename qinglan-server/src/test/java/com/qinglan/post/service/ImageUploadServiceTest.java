package com.qinglan.post.service;

import com.qinglan.common.error.BizException;
import com.qinglan.common.error.ErrorCode;
import com.qinglan.common.storage.StorageService;
import com.qinglan.common.store.InMemoryEphemeralStore;
import com.qinglan.post.config.UploadProperties;
import com.qinglan.post.dto.UploadedImageResponse;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 图片上传的校验与限流（T-12 H）。
 */
@ExtendWith(MockitoExtension.class)
class ImageUploadServiceTest {

    private static final long USER_ID = 7L;
    private static final int MAX_PER_WINDOW = 2;
    private static final String STORED_URL = "/uploads/2026/10/" + "a".repeat(32) + ".jpg";

    /** 19 字节的 JPEG 文件头，长度满足 ImageType.detect 的最小要求 */
    private static final byte[] JPEG_CONTENT =
            HexFormat.of().parseHex("ffd8ffe000104a46494600010100000100010000");

    @Mock
    private StorageService storageService;

    @Mock
    private UserService userService;

    private ImageUploadService imageUploadService;

    @BeforeEach
    void setUp() {
        UploadProperties uploadProperties = new UploadProperties(MAX_PER_WINDOW, Duration.ofMinutes(1));
        imageUploadService = new ImageUploadService(
                storageService, userService, new InMemoryEphemeralStore(), uploadProperties);
    }

    @Test
    @DisplayName("合法图片按真实类型落盘，返回可访问地址")
    void shouldStoreValidImage() {
        when(storageService.store(any(byte[].class), anyString())).thenReturn(STORED_URL);

        UploadedImageResponse response =
                imageUploadService.upload(USER_ID, new MockMultipartFile("file", JPEG_CONTENT));

        assertThat(response.url()).isEqualTo(STORED_URL);
        verify(userService).requireUsableById(USER_ID);
        verify(storageService).store(any(byte[].class), anyString());
    }

    @Test
    @DisplayName("H：超出窗口内的上传次数上限即拒绝，且不再落盘")
    void shouldRejectWhenRateExceeded() {
        when(storageService.store(any(byte[].class), anyString())).thenReturn(STORED_URL);
        MockMultipartFile file = new MockMultipartFile("file", JPEG_CONTENT);

        imageUploadService.upload(USER_ID, file);
        imageUploadService.upload(USER_ID, file);

        assertThatThrownBy(() -> imageUploadService.upload(USER_ID, file))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.UPLOAD_TOO_FREQUENT);
        verify(storageService, times(MAX_PER_WINDOW)).store(any(byte[].class), anyString());
    }

    @Test
    @DisplayName("H：限流先于格式校验，坏文件同样消耗配额，无法绕过频次限制刷存储")
    void shouldCountInvalidAttemptsAgainstQuota() {
        MockMultipartFile bad = new MockMultipartFile("file", "not-an-image".getBytes(StandardCharsets.UTF_8));

        for (int attempt = 0; attempt < MAX_PER_WINDOW; attempt++) {
            assertThatThrownBy(() -> imageUploadService.upload(USER_ID, bad))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).errorCode())
                    .isEqualTo(ErrorCode.UPLOAD_INVALID);
        }

        assertThatThrownBy(() -> imageUploadService.upload(USER_ID, bad))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.UPLOAD_TOO_FREQUENT);
    }

    @Test
    @DisplayName("空文件被拒绝")
    void shouldRejectEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);

        assertThatThrownBy(() -> imageUploadService.upload(USER_ID, empty))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.UPLOAD_INVALID);
    }
}
