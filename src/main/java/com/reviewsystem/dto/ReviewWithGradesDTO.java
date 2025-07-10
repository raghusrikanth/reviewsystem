package com.reviewsystem.dto;

import com.reviewsystem.model.Review;
import com.reviewsystem.model.ReviewGrades;
import java.time.LocalDateTime;
import java.util.List;

public class ReviewWithGradesDTO {
    public Long reviewId;
    public Long hotelId;
    public String hotelName;
    public Long reviewerId;
    public String reviewerName;
    public Double rating;
    public String reviewComments;
    public LocalDateTime reviewDate;
    public List<ReviewGrades> grades;

    public ReviewWithGradesDTO(Review review) {
        this.reviewId = review.getId();
        this.hotelId = review.getHotel() != null ? review.getHotel().getId() : null;
        this.hotelName = review.getHotel() != null ? review.getHotel().getName() : null;
        this.reviewerId = review.getReviewer() != null ? review.getReviewer().getId() : null;
        this.reviewerName = review.getReviewer() != null ? review.getReviewer().getDisplayName() : null;
        this.rating = review.getRating();
        this.reviewComments = review.getReviewComments();
        this.reviewDate = review.getReviewDate();
        this.grades = review.getGrades();
    }
} 