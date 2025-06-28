package com.example.google.google_hackathon.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// @Autowired のフィールドは推奨されないため、削除。コンストラクタインジェクションに集約
// import org.springframework.beans.factory.annotation.Autowired;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpMethod;

// ロギングのためのimportを追加
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.google.google_hackathon.security.JwtAuthenticationFilter;
import com.example.google.google_hackathon.security.JwtTokenProvider;
import com.example.google.google_hackathon.service.CustomOAuth2UserService;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.google.google_hackathon.entity.AppUser; // AppUserエンティティをインポート

@Configuration
@EnableWebSecurity // このアノテーションでSpring SecurityのWebセキュリティ機能を有効化
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    // アプリケーション独自のJWT認証のために必要な依存関係
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.frontend.redirect-url}")
    private String frontendRedirectBaseUrl; // プロパティを保持

    // コンストラクタで必要な依存関係のみを注入
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            CustomUserDetailsService customUserDetailsService,
            JwtTokenProvider jwtTokenProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults()) // CORS設定を適用
                .csrf(AbstractHttpConfigurer::disable) // CSRFを無効化
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // セッションをステートレスに設定
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/login").permitAll()
                        .requestMatchers("/ws/**", "/topic/**", "/app/**").permitAll()
                        // OAuth2認証関連のURIをpermitAll()にする
                        // GoogleからのコールバックURI (/oauth2/callback/*) は、ここに含まれるようにします
                        // "/error" はWhitelabel Error Pageが表示される原因となるためpermitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/code/**", "/error").permitAll()

                        // ロードマップエントリ（/api/roadmap-entries）の設定
                        .requestMatchers(HttpMethod.GET, "/api/roadmap-entries").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/roadmap-entries").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/roadmap-entries/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/roadmap-entries/**").authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, "/api/roadmap-entries/**").permitAll() // OPTIONSリクエストもCORSのために許可

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
                        .requestMatchers("/api/reflections/**").authenticated()
                        .requestMatchers("/api/roadmap/generate").permitAll()

                        .requestMatchers("/api/reflections/**").authenticated()
                        .requestMatchers("/api/roadmap/generate").permitAll()

                        // Google Calendar API 連携のエンドポイントは認証が必要
                        // ここでアプリケーション独自のJWT認証を要求する
                        .requestMatchers("/api/calendar/**").authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, excep) -> {
                            logger.warn("認証されていないリクエストが拒否されました: URI={}, 例外={}: {}",
                                    req.getRequestURI(), excep.getClass().getName(), excep.getMessage());
                            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Googleカレンダー連携専用のOAuth2 Login設定を適用
                // calendarServiceOAuth2Login() Beanで定義したCustomizerをoauth2Loginメソッドに直接渡します。
                .oauth2Login(calendarServiceOAuth2Login()) // ← このように修正
                .build();
    }

    // Googleカレンダー連携専用のOAuth2Login設定をBeanとして定義
    @Bean
    public Customizer<org.springframework.security.config.annotation.web.configurers.oauth2.client.OAuth2LoginConfigurer<HttpSecurity>> calendarServiceOAuth2Login() {
        return oauth2 -> oauth2
                // カレンダー連携専用の認証開始URI
                .authorizationEndpoint(authorization -> authorization
                        .baseUri("/oauth2/authorization/google-calendar") // 例: /oauth2/authorization/google-calendar
                )
                // カレンダー連携専用のコールバックURI
                .redirectionEndpoint(redirection -> redirection
                        .baseUri("/oauth2/callback/google-calendar") // 例: /oauth2/callback/google-calendar
                )
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(oAuth2UserService()) // カスタムOAuth2UserServiceを登録
                )
                // 認証成功時のハンドラー
                .successHandler(googleCalendarOAuth2SuccessHandler())
                // 認証失敗時のハンドラー
                .failureHandler((request, response, exception) -> {
                    logger.error("Google Calendar OAuth2 Login Failed: {} URI: {}", exception.getMessage(),
                            request.getRequestURI(), exception);
                    String encodedErrorMessage = java.net.URLEncoder.encode(exception.getMessage(), "UTF-8");
                    // フロントエンドのGoogle連携失敗ページなどへリダイレクト
                    response.sendRedirect(
                            frontendRedirectBaseUrl + "/google-calendar-auth-failed?error=" + encodedErrorMessage);
                });
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "https://my-frontimage-14467698004.asia-northeast1.run.app",
                "http://localhost:8081",
                "http://localhost:3000" // ここに http://localhost:3000 を追加
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        logger.info("CORS設定を初期化しました。許可されるオリジン: {}", configuration.getAllowedOrigins());
        return source;
    }

    // RestTemplate の Bean 定義
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // CustomOAuth2UserService はDBに何も保存しないため、依存性なし
    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
        return new CustomOAuth2UserService();
    }

    // Googleカレンダー連携のためのOAuth2認証成功ハンドラー
    // ここではJWTを発行せず、単にフロントエンドにリダイレクトして、
    // フロントエンド側でGoogleアクセストークン（クライアント側で管理）を使ってAPIを叩けるようにする。
    @Bean
    public AuthenticationSuccessHandler googleCalendarOAuth2SuccessHandler() {
        return (request, response, authentication) -> {
            logger.info("Google Calendar OAuth2 認証成功！");

            // Google認証成功後、直接フロントエンドのリダイレクトURLへ送り返す。
            // ここではバックエンドのJWTは発行しない。
            // フロントエンドはここでGoogleから受け取ったアクセストークンを
            // 自前で取得・管理・利用する想定。
            response.sendRedirect(frontendRedirectBaseUrl + "/roadmap");
        };
    }
}