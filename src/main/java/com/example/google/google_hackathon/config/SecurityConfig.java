package com.example.google.google_hackathon.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpMethod;

import com.example.google.google_hackathon.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
                        // ロードマップエントリ（/api/roadmap-entries）の設定
                        .requestMatchers(HttpMethod.GET, "/api/roadmap-entries").permitAll()
                        // POST/PUT/DELETEは認証済みユーザーのみ
                        .requestMatchers(HttpMethod.POST, "/api/roadmap-entries").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/roadmap-entries/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/roadmap-entries/**").authenticated()
                        // OPTIONSリクエストもCORSのために許可
                        .requestMatchers(HttpMethod.OPTIONS, "/api/roadmap-entries/**").permitAll()
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
}
