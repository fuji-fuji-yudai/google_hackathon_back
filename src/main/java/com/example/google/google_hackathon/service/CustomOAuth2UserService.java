package com.example.google.google_hackathon.service;

// AppUser エンティティや AppUserRepository は、このサービスでDBに何も保存しないためインポート不要
// import com.example.google.google_hackathon.entity.AppUser;
// import com.example.google.google_hackathon.repository.AppUserRepository;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User; // OAuth2Userの具体的な実装
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    // 依存関係がなくなったため、引数なしのコンストラクタが適切です
    public CustomOAuth2UserService() {
        // このサービスはSpring SecurityのOAuth2認証フローの一部として、
        // Googleから受け取ったユーザー情報をそのまま返す役割に特化
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // デフォルトのOAuth2UserServiceがGoogleからユーザー情報を取得します
        OAuth2User oauth2User = super.loadUser(userRequest);
        logger.info("OAuth2Userがロードされました。名前: {}, 属性: {}", oauth2User.getName(), oauth2User.getAttributes());

        return new DefaultOAuth2User(
                oauth2User.getAuthorities(), // Googleによって付与された権限
                oauth2User.getAttributes(), // Googleユーザーの各種属性（ID、名前、メールなど）
                "sub" // ユーザーを識別するための属性のキー（Googleのsub ID）
        );
    }
}