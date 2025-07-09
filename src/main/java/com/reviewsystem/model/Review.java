package com.reviewsystem.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "review")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Review {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id")
    private Reviewer reviewer;

    private Double rating;
    private String checkInMonthYear;
    private String encryptedReviewData;
    private String formattedRating;
    private String formattedReviewDate;
    private String ratingText;
    private String responderName;
    private String responseDateText;
    private String responseTranslateSource;
    @Lob
    @Column(name = "review_comments", columnDefinition = "LONGTEXT")
    private String reviewComments;
    @Lob
    @Column(name = "review_negatives", columnDefinition = "LONGTEXT")
    private String reviewNegatives;
    @Lob
    @Column(name = "review_positives", columnDefinition = "LONGTEXT")
    private String reviewPositives;
    private String reviewProviderLogo;
    private String reviewProviderText;
    private String reviewTitle;
    private String translateSource;
    private String translateTarget;
    private LocalDateTime reviewDate;
    @Lob
    @Column(name = "original_title", columnDefinition = "LONGTEXT")
    private String originalTitle;
    @Lob
    @Column(name = "original_comment", columnDefinition = "LONGTEXT")
    private String originalComment;
    private String formattedResponseDate;
    private Boolean isShowReviewResponse;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
} 