package com.example.google.google_hackathon.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.google.google_hackathon.entity.ReflectionEntity;

public interface ReflectionRepository extends JpaRepository<ReflectionEntity, Long> {
}