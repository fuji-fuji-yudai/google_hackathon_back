package com.example.google.google_hackathon.config;

import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import com.example.google.google_hackathon.service.CustomUserDetailsService;

@Configuration // このクラスはSpringの設定クラスとして扱われる
public class SecurityConfig {

    /**
     * アプリケーション全体のセキュリティ設定を定義する。
     * - 全リクエストを認証必須とする
     * - デフォルトのログインフォーム (/login) を使う
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 全てのHTTPリクエストに対して認証を要求
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            // デフォルトのログインフォーム (/login) を有効化
            .formLogin(form -> form
                .permitAll() // 誰でもログインページにはアクセス可能
            );

        return http.build(); // SecurityFilterChain を返す
    }

    /**
     * パスワードの暗号化方式を定義する。
     * - BCrypt を使用（セキュアなハッシュ方式）
     * - ユーザー登録時やログイン時に利用される
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
         System.out.println("PasswordEncoder Bean initialized");
        return new BCryptPasswordEncoder();
    }

    /**
     * 認証マネージャーを取得する。
     * - Spring Boot が自動構成した AuthenticationManager を使用
     * - UserDetailsService や PasswordEncoder の設定に基づいて動作
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

}
