package com.example.google.google_hackathon.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * アプリケーション全体で共有される Bean (PasswordEncoderなど) を定義する設定クラスです。
 * SecurityConfig から PasswordEncoder の定義を分離し、循環参照を防ぎます。
 */
@Configuration
public class PasswordEncoderConfig {

    private static final Logger logger = LoggerFactory.getLogger(PasswordEncoderConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        logger.info("PasswordEncoder Bean (BCryptPasswordEncoder) initialized in PasswordEncoderConfig.");
        return new BCryptPasswordEncoder();
    }
}