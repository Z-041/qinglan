package com.qinglan.post.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 孤儿回收配置的默认值：老的本机 application.yml 没有 orphan-* 两项，缺省时必须能启动。
 * （本机 application.yml 已被 gitignore，不能让「本地起不来」这种问题等到部署才发现。）
 */
class UploadPropertiesTest {

    @Test
    @DisplayName("缺 orphan-* 时取默认：开启回收、保留期 24h")
    void shouldApplyDefaultsWhenOrphanKeysAbsent() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "qinglan.upload.max-per-window", 30,
                "qinglan.upload.window", "1m")));

        UploadProperties properties = binder
                .bind("qinglan.upload", Bindable.of(UploadProperties.class))
                .get();

        assertThat(properties.orphanCleanupEnabled()).isTrue();
        assertThat(properties.orphanRetention()).isEqualTo(Duration.ofHours(24));
    }
}
