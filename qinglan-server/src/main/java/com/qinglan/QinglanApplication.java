package com.qinglan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 青栏（Qinglan）后端入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class QinglanApplication {

    public static void main(String[] args) {
        SpringApplication.run(QinglanApplication.class, args);
    }
}
