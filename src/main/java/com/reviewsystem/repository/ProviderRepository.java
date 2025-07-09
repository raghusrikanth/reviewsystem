package com.reviewsystem.repository;

import com.reviewsystem.model.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, Long> {
} 