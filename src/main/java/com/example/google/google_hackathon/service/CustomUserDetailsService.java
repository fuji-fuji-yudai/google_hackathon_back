package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

// import java.util.*;

@Service // Spring がこのクラスをサービス（DI対象）として認識するためのアノテーション
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired // AppUserRepository を自動注入（DBアクセス用）
    private AppUserRepository AppUserRepository;

    
public CustomUserDetailsService(AppUserRepository userRepository) {
        this.AppUserRepository = userRepository;
        }


    /**
     * ユーザー名をもとにデータベースからユーザー情報を取得し、
     * Spring Security が使える UserDetails に変換して返す。
     *
     * このメソッドは、Spring Security によって自動的に呼ばれる。
     *
     * username ログインフォームで入力されたユーザー名
     * UserDetails オブジェクト（認証処理に使用される）
     * throws UsernameNotFoundException ユーザーが見つからなかった場合にスローされる例外
     */

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
       try{ // username をもとに DB からユーザーを検索（見つからない場合は例外を投げる）
        AppUser user = AppUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        System.out.println(">>> ユーザー名: " + username);
        System.out.println(">>> DBから取得したパスワード: " + user.getPassword());
        System.out.println(">>> 権限: " + user.getRole());

        // DBのユーザー情報を Spring Security 用の UserDetails に変換して返す
        return User.builder()
                .username(user.getUsername()) // ユーザー名
                .password(user.getPassword()) // ハッシュ化されたパスワード
                .roles(user.getRole().replace("ROLE_", "")) // 権限（"ROLE_" は自動付与されるので除去）
                .build();
    
} catch (Exception e) {
e.printStackTrace(); // ここで例外の詳細を出力
 throw e;
}

}
}
