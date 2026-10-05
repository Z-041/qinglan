package com.qinglan.common.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 挂载本地存储：把上传目录暴露为静态资源，并注册 {@link StorageService}。
 *
 * <p>换成对象存储实现时，本类只需替换 {@link #storageService} 这个 Bean，
 * 资源映射可以一并去掉（文件由对象存储直接对外提供）。</p>
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageWebConfig implements WebMvcConfigurer {

    private final StorageProperties properties;

    public StorageWebConfig(StorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public StorageService storageService() {
        return new LocalStorageService(root());
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(root().toUri().toString());
    }

    private Path root() {
        return Path.of(properties.localRoot() == null ? "./data/uploads" : properties.localRoot());
    }
}
