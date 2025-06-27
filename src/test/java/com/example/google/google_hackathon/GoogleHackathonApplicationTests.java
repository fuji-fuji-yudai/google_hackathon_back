package com.example.google.google_hackathon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource; // ★追加: このimport文を追加します

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

}