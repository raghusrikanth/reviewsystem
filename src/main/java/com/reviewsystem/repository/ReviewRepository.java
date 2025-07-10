package com.reviewsystem.repository;

import com.reviewsystem.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    java.util.List<Review> findByReviewerId(Long reviewerId);
    java.util.List<Review> findByHotelId(Long hotelId);
} 