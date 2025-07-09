package com.reviewsystem.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "reviewer")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Reviewer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String displayName;
    private String countryName;
    private String flagName;
    private String reviewGroupName;
    private String roomTypeName;
    private Integer countryId;
    private Integer lengthOfStay;
    private Integer reviewGroupId;
    private Integer roomTypeId;
    private Integer reviewedCount;
    private Boolean isExpertReviewer;
    private Boolean isShowGlobalIcon;
    private Boolean isShowReviewedCount;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
} 