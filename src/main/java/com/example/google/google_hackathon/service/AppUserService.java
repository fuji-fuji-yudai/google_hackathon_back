package com.example.google.google_hackathon.service;

import com.example.google.google_hackathon.entity.AppUser;
import com.example.google.google_hackathon.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AppUserService {

    @Autowired
    private AppUserRepository appUserRepository;

    public Long getUserIdByUsername(String username) {
        return appUserRepository.findByUsername(username)
                .map(AppUser::getId)
                .orElse(null); // または例外を投げる
    }
}
