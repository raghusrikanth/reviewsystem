package com.reviewsystem.dto;

import java.time.LocalDateTime;

public class OverallByProviderDTO {
    public Long id;
    public Long reviewId;
    public Long providerId;
    public String providerName;
    public Double overallScore;
    public Integer reviewCount;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public OverallByProviderDTO(com.reviewsystem.model.OverallByProvider obp) {
        this.id = obp.getId();
        this.reviewId = obp.getReview() != null ? obp.getReview().getId() : null;
        this.providerId = obp.getProvider() != null ? obp.getProvider().getId() : null;
        this.providerName = obp.getProvider() != null ? obp.getProvider().getName() : null;
        this.overallScore = obp.getOverallScore();
        this.reviewCount = obp.getReviewCount();
        this.createdAt = obp.getCreatedAt();
        this.updatedAt = obp.getUpdatedAt();
    }
} 