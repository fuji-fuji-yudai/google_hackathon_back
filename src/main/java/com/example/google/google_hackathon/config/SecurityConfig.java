package com.example.google.google_hackathon.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // WebSecurity を有効にするアノテーション
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails; //UserDetails をインポート
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken; //OAuth2AuthenticationToken をインポート
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest; //OAuth2UserRequest をインポート
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService; //OAuth2UserService をインポート
import org.springframework.security.oauth2.core.user.OAuth2User; //OAuth2User をインポート
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler; // AuthenticationSuccessHandler をインポート
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpMethod;

import com.example.google.google_hackathon.security.JwtAuthenticationFilter;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import com.example.google.google_hackathon.service.CustomOAuth2UserService;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.service.CustomUserDetailsService;

@Configuration
@EnableWebSecurity // このアノテーションでSpring SecurityのWebセキュリティ機能を有効化
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // Google OAuth2ログイン連携のために必要な依存関係を注入
    private final AppUserRepository appUserRepository;
    private final CustomUserDetailsService customUserDetailsService; // JWT認証とOAuth2認証両方で使用
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleAuthTokenRepository googleAuthTokenRepository;

    // コンストラクタに新しく追加した依存関係を注入
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            AppUserRepository appUserRepository,
            CustomUserDetailsService customUserDetailsService,
            JwtTokenProvider jwtTokenProvider,
            GoogleAuthTokenRepository googleAuthTokenRepository) { // ★この行と上の宣言部分を追加
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appUserRepository = appUserRepository;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login").permitAll()
                        .requestMatchers("/ws/**", "/topic/**", "/app/**").permitAll()
                        // OAuth2認証関連のURIをpermitAll()にする
                        .requestMatchers("/oauth2/**", "/login/oauth2/code/**", "/error").permitAll()
                        // ロードマップエントリ（/api/roadmap-entries）の設定
                        .requestMatchers(HttpMethod.GET, "/api/roadmap-entries").permitAll()
                        // POST/PUT/DELETEは認証済みユーザーのみ
                        .requestMatchers(HttpMethod.POST, "/api/roadmap-entries").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/roadmap-entries/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/roadmap-entries/**").authenticated()
                        // OPTIONSリクエストもCORSのために許可
                        .requestMatchers(HttpMethod.OPTIONS, "/api/roadmap-entries/**").permitAll()

                        // リマインダー（/api/reminders）の認証設定
                        .requestMatchers(HttpMethod.GET, "/api/reminders").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reminders").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/reminders/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reminders/**").authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/reminders/**").permitAll()

                        // task
                        .requestMatchers(HttpMethod.GET, "/api/tasks").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/tasks").authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/tasks").permitAll()
                        .requestMatchers("/api/reflections/**").authenticated() // 追加
                        .requestMatchers("/api/roadmap/generate").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, excep) -> {
                            System.out.println("=== AuthenticationEntryPoint called ===");
                            System.out.println("Request URI: " + req.getRequestURI());
                            System.out.println("Exception: " + excep.getMessage());
                            System.out.println("Exception class: " + excep.getClass().getName());
                            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // ここからがGoogle OAuth2 Login のための新規追加部分
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization") // OAuth2認証を開始するURI (例: /oauth2/authorization/google)
                        )
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/oauth2/callback/*") // GoogleからのリダイレクトURIのパターン (例: /oauth2/callback/google)
                        )
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService()) // カスタムOAuth2UserServiceを登録
                        )
                        .successHandler(authenticationSuccessHandler()) // 認証成功時のハンドラー
                        .failureHandler((request, response, exception) -> {
                            System.err.println("OAuth2 Login Failed: " + exception.getMessage());
                            // エラー発生時にフロントエンドのログインページにリダイレクトなど
                            response.sendRedirect(
                                    "http://localhost:3000/login?auth_failed=true&error=" + exception.getMessage());
                        }))
                .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        System.out.println("PasswordEncoder Bean initialized");
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "https://my-frontimage-14467698004.asia-northeast1.run.app",
                "http://localhost:8081"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // RestTemplate の Bean 定義
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // ObjectMapper の Bean 定義（AppConfig.java に移動することを強く推奨しますが、このまま残す場合は新規追加）
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    // CustomOAuth2UserServiceのBean定義
    // Google認証成功時にユーザー情報を処理し、GoogleAuthTokenを保存
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
        return new CustomOAuth2UserService(googleAuthTokenRepository, appUserRepository, jwtTokenProvider);
    }

    // OAuth2認証成功時のハンドラー
    // Google認証成功後、JWTトークンを生成し、フロントエンドにリダイレクト
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
            OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

            // Googleのメールアドレスをユーザー名として利用し、既存のCustomUserDetailsServiceでUserDetailsをロード
            String username = oauth2User.getAttribute("email");
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);

            // JWTトークンを生成
            // String jwt = jwtTokenProvider.generateToken(userDetails);
            String jwt = jwtTokenProvider.generateToken(authentication);

            // フロントエンドにリダイレクトし、JWTトークンをURLパラメータで渡す
            // 例: http://localhost:3000/roadmap?jwt=YOUR_JWT_TOKEN
            // セキュリティのため、本番環境ではHTTPSが必須です。
            String frontendRedirectUrl = "http://localhost:3000/roadmap?jwt=" + jwt; // フロントエンドのダッシュボードなど
            response.sendRedirect(frontendRedirectUrl);
        };
    }
}
