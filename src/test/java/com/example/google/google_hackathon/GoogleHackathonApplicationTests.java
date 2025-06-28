package com.example.google.google_hackathon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.mock.mockito.MockBean; // ★これをインポート

import com.example.google.google_hackathon.service.GoogleCalendarService; // ★これもインポート
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest
@TestPropertySource(properties = {
		"app.frontend.redirect-url=https://my-frontimage-14467698004.asia-northeast1.run.app",
		"app.jwt.secret=test-secret-key-for-jwt-testing-only-at-least-64-bytes-long-for-hs512-algorithm",
		"app.jwt.expiration-ms=3600000",
// アプリケーションが他のDBや外部サービスに接続を試みる場合、
// テスト用プロパティをここに追加するか、そのBeanをモック化する必要があります。
// 例: "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
// 例: "spring.datasource.driver-class-name=org.h2.Driver"
// 例: "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GoogleHackathonApplicationTests {

	// ★追加: GoogleCalendarService をモック化
	// これにより、テスト実行時に Google Calendar API への実際の呼び出しが行われず、
	// APIキーやネットワーク接続の問題でコンテキストロードが失敗するのを防ぎます。
	@MockBean
	private GoogleCalendarService googleCalendarService;

	@Test
	void contextLoads() {
		// このテストは、Spring ApplicationContextが正常にロードされるかを確認します。
		// ここにコードを追加する必要は通常ありません。
	}

	@SpringBootApplication
	public class GoogleHackathonApplication {

		public static void main(String[] args) {
			SpringApplication.run(GoogleHackathonApplication.class, args);
		}

		@Bean
		public ObjectMapper objectMapper() {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.registerModule(new JavaTimeModule());
			return objectMapper;
		}
	}
}