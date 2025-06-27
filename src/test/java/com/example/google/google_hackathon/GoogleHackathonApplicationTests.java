package com.example.google.google_hackathon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource; // ★追加: このimport文を追加します

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootTest
// ★追加: このアノテーションを追加し、不足しているプロパティを直接設定します
@TestPropertySource(properties = {
		"app.frontend.redirect-url=https://my-frontimage-14467698004.asia-northeast1.run.app"
})
class GoogleHackathonApplicationTests {

	@Test
	void contextLoads() {
		// このテストは、Spring ApplicationContextが正常にロードされるかを確認します。
		// これまでに発生したBeanの依存関係の問題やプロパティの解決問題を捉えるためのものです。
	}

	@SpringBootApplication
	public class GoogleHackathonApplication {

		public static void main(String[] args) {
			SpringApplication.run(GoogleHackathonApplication.class, args);
		}

		@Bean
		public ObjectMapper objectMapper() {
			ObjectMapper objectMapper = new ObjectMapper();
			objectMapper.registerModule(new JavaTimeModule()); // ここでモジュールが登録されているか
			return objectMapper;
		}
	}
}