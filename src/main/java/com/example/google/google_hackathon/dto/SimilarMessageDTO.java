package com.example.google.google_hackathon.dto;

import java.util.Date;

import com.example.google.google_hackathon.service.SimilarityService.SimilarMessage;

public record SimilarMessageDTO(String message, double similarity, String sender, Date timestamp) {
    public static SimilarMessageDTO from(SimilarMessage sm) {
        return new SimilarMessageDTO(sm.getMessage(), sm.getSimilarity(), sm.getSender(), sm.getTimestamp());
    }
}
