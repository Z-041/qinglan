package com.qinglan.user;

import com.qinglan.common.crypto.CryptoService;
import com.qinglan.user.dto.UserProfileResponse;
import com.qinglan.user.entity.User;
import com.qinglan.user.repository.UserRepository;
import com.qinglan.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不变量 #3：真实身份管理员不可见 —— 通过真实 MySQL 验证学号落库形态与对外档案。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IdentityEncryptionTest {

    private static final String STUDENT_ID = "2023010199";

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CryptoService cryptoService;

    @Test
    @DisplayName("学号以密文入库，可解密回原值，且不回传任何身份字段")
    void shouldStoreEncryptedStudentIdAndHideItFromProfile() {
        String emailHash = cryptoService.hashEmail("identity@qinglan.edu.cn");

        User created = userService.createVerifiedStudent(emailHash, STUDENT_ID);
        User loaded = userRepository.findById(created.getId()).orElseThrow();

        assertThat(loaded.getStudentIdEnc()).isNotEqualTo(STUDENT_ID);
        assertThat(cryptoService.decrypt(loaded.getStudentIdEnc())).isEqualTo(STUDENT_ID);
        assertThat(loaded.getStudentIdHash()).isNotEqualTo(STUDENT_ID);
        assertThat(loaded.getEmailHash()).isNotEqualTo("identity@qinglan.edu.cn");

        UserProfileResponse profile = userService.toProfile(loaded);
        assertThat(profile.toString())
                .doesNotContain(STUDENT_ID)
                .doesNotContain("identity@qinglan.edu.cn");

        Set<String> componentNames = Arrays.stream(UserProfileResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .map(name -> name.toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
        assertThat(componentNames).noneMatch(name -> name.contains("email") || name.contains("student"));
    }
}
