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
import com.example.google.google_hackathon.repository.GoogleAuthTokenRepository;
import com.example.google.google_hackathon.service.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.google.google_hackathon.entity.AppUser; // AppUserエンティティをインポート
import com.example.google.google_hackathon.entity.GoogleAuthToken;

@Configuration
@EnableWebSecurity // このアノテーションでSpring SecurityのWebセキュリティ機能を有効化
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    // Google OAuth2ログイン連携のために必要な依存関係を注入
    private final AppUserRepository appUserRepository;
    private final CustomUserDetailsService customUserDetailsService; // JWT認証とOAuth2認証両方で使用
    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleAuthTokenRepository googleAuthTokenRepository;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired // ★修正: PasswordEncoder はコンストラクタではなく、@Autowired で注入します
    private PasswordEncoder passwordEncoder; // ★修正: final を削除し、@Autowired を付ける

    @Value("${app.frontend.redirect-url}")
    private String frontendRedirectBaseUrl; // プロパティを保持

    // コンストラクタに全ての依存関係を注入（Spring Boot 2.x以降では@Autowiredを省略可能）
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AppUserRepository appUserRepository,
            CustomUserDetailsService customUserDetailsService,
            JwtTokenProvider jwtTokenProvider,
            GoogleAuthTokenRepository googleAuthTokenRepository,
            PasswordEncoder passwordEncoder) { // PasswordEncoder をコンストラクタに追加
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.appUserRepository = appUserRepository;
        this.customUserDetailsService = customUserDetailsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.googleAuthTokenRepository = googleAuthTokenRepository;
        // this.passwordEncoder = passwordEncoder; // 注入された PasswordEncoder を設定
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
                        .anyRequest().authenticated()) // その他の全てのリクエストは認証が必要
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, excep) -> {
                            logger.warn("認証されていないリクエストが拒否されました: URI={}, 例外={}: {}",
                                    req.getRequestURI(), excep.getClass().getName(), excep.getMessage());
                            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class) // JWTフィルターを通常認証フィルターの前に配置
                // Google OAuth2 Login のための設定
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/oauth2/authorization") // OAuth2認証を開始するURI (例: /oauth2/authorization/google)
                        )
                        .redirectionEndpoint(redirection -> redirection
                                // GoogleからのリダイレクトURIのパターン
                                // 以前のエラーログで示された正確なURIに合わせて /oauth2/callback を使用
                                // Spring Securityはこれを処理する
                                .baseUri("/oauth2/callback"))
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService()) // カスタムOAuth2UserServiceを登録 (別途定義されているはず)
                        )
                        // ★ここを修正しました★ 認証成功時のハンドラーをインラインで定義し、フロントエンドにリダイレクト
                        .successHandler((request, response, authentication) -> {
                            logger.info("OAuth2 Login Success! User: {}", authentication.getName());
                            // ここにあなたのVue.jsフロントエンドのReminderViewへの正確なURLを設定してください
                            // application.propertiesから読み込む場合は frontendRedirectBaseUrl + "/reminder-view"
                            // のように
                            response.sendRedirect(
                                    "https://my-frontimage-14467698004.asia-northeast1.run.app/reminder-view");
                        })
                        .failureHandler((request, response, exception) -> {
                            logger.error("OAuth2 Login Failed: {} URI: {}", exception.getMessage(),
                                    request.getRequestURI(), exception);
                            String encodedErrorMessage = java.net.URLEncoder.encode(exception.getMessage(), "UTF-8");
                            response.sendRedirect(
                                    // フロントエンドのログインページにリダイレクト
                                    // frontendRedirectBaseUrl を使用する場合
                                    // frontendRedirectBaseUrl + "/login?auth_failed=true&error=" +
                                    // encodedErrorMessage);
                                    "https://my-frontimage-14467698004.asia-northeast1.run.app/login?auth_failed=true&error="
                                            + encodedErrorMessage);
                        }))
                .build(); // http.build() を最後に呼び出す
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
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

    @Bean
    public OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService() {
        // CustomOAuth2UserServiceのコンストラクタにPasswordEncoderを追加して渡す
        return new CustomOAuth2UserService(googleAuthTokenRepository, appUserRepository, jwtTokenProvider,
                passwordEncoder);
    }

    // OAuth2認証成功時のハンドラー
    // Google認証成功後、JWTトークンを生成し、フロントエンドにリダイレクト
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // Googleログインの人かチェック。
            if (!(authentication instanceof OAuth2AuthenticationToken)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return;
            }

            // GoogleのID（番号）をもらうよ。
            String googleId = ((OAuth2User) authentication.getPrincipal()).getName();

            // GoogleのIDから、Googleのログイン情報（GoogleAuthToken）を見つけるよ。
            // これがないと、どのAppUserに紐づくか分からないよ。
            GoogleAuthToken googleAuthToken = googleAuthTokenRepository.findByGoogleSubId(googleId)
                    .orElseThrow(() -> new IllegalStateException("Googleログイン情報が見つかりません。"));

            // Googleのログイン情報から、アプリのユーザーのIDをもらうよ。
            Long appUserId = googleAuthToken.getAppUserId();

            // アプリのユーザーIDを使って、アプリのユーザー（AppUser）を見つけるよ。
            // これがないと、UserDetailsをロードできないよ。
            AppUser appUser = appUserRepository.findById(appUserId)
                    .orElseThrow(() -> new IllegalStateException("アプリのユーザーが見つかりません。"));

            String secretPass = jwtTokenProvider.generateToken(authentication);

            // 「秘密のパス」を持って、ウェブサイトの別の場所へ行ってもらうよ。
            String goToUrl = "https://my-frontimage-14467698004.asia-northeast1.run.app/callback?token=" + secretPass;
            response.sendRedirect(goToUrl);
        };
    }
}
