package com.reviewsystem.repository;

import com.reviewsystem.model.OverallByProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverallByProviderRepository extends JpaRepository<OverallByProvider, Long> {
    java.util.List<OverallByProvider> findByReview_Hotel_IdOrderByProviderIdAscCreatedAtDesc(Long hotelId);
} 