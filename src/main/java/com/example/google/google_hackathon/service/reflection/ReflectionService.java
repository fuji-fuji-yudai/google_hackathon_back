package com.example.google.google_hackathon.service.reflection;

import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.entity.ReflectionEntity;
import com.example.google.google_hackathon.repository.AppUserRepository;
import com.example.google.google_hackathon.repository.ReflectionRepository;

@Service
public class ReflectionService {
  private final ReflectionRepository reflectionRepository;
  private final AppUserRepository appUserRepository;
  @Autowired
  public ReflectionService(ReflectionRepository reflectionRepository, AppUserRepository appUserRepository) {
    this.reflectionRepository = reflectionRepository;
    this.appUserRepository = appUserRepository;
  }

  public ReflectionEntity createReflection(ReflectionEntity reflectionEntity, String userName) throws SQLException {
    System.out.println("登録処理開始");
    AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    reflectionEntity.setUserID(user.getId());
    System.out.println(reflectionEntity.getID());
    return reflectionRepository.save(reflectionEntity);
  }
}
