package com.example.google.google_hackathon.service.reflection;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

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

  public List<ReflectionEntity> getReflectionsByMonth(int year, int month, String userName) throws SQLException {
    System.out.println("振り返りデータ取得処理開始");
    AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    // 月の開始日と終了日を計算
    LocalDate startDate = LocalDate.of(year, month, 1);
    LocalDate endDate = startDate.with(TemporalAdjusters.lastDayOfMonth());
    // データベースから取得
    return reflectionRepository.findByUserIdAndDateBetween(user.getId(), Date.valueOf(startDate), Date.valueOf(endDate));
  }

  public ReflectionEntity createReflection(ReflectionEntity reflectionEntity, String userName) throws SQLException {
    System.out.println("登録処理開始");
    AppUser user = appUserRepository.findByUsername(userName)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    reflectionEntity.setUserId(user.getId());
    System.out.println("取得したユーザーID : " + reflectionEntity.getUserId());
    return reflectionRepository.save(reflectionEntity);
  }
}
