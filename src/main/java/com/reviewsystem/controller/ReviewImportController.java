package com.reviewsystem.controller;

import com.reviewsystem.service.ReviewImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.reviewsystem.repository.ReviewRepository;
import com.reviewsystem.repository.OverallByProviderRepository;
import com.reviewsystem.model.Review;
import com.reviewsystem.model.OverallByProvider;
import java.util.*;
import com.reviewsystem.dto.ReviewWithGradesDTO;
import com.reviewsystem.dto.OverallByProviderDTO;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewImportController {
    private final ReviewImportService reviewImportService;
    private final ReviewRepository reviewRepository;
    private final OverallByProviderRepository overallByProviderRepository;

    @PostMapping("/import-jl")
    public ResponseEntity<String> importJLFile() {
        reviewImportService.importJLFiles();
        return ResponseEntity.ok("Import started");
    }

    @PostMapping("/import-jl-folder")
    public ResponseEntity<String> importJLFolder() {
        reviewImportService.importJLFiles();
        return ResponseEntity.ok("Import started for folder");
    }

    @GetMapping("/by-user/{userId}")
    public ResponseEntity<List<ReviewWithGradesDTO>> getReviewsByUser(@PathVariable Long userId) {
        List<Review> reviews = reviewRepository.findByReviewerId(userId);
        List<ReviewWithGradesDTO> dtos = new ArrayList<>();
        for (Review r : reviews) dtos.add(new ReviewWithGradesDTO(r));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/by-hotel/{hotelId}")
    public ResponseEntity<List<ReviewWithGradesDTO>> getReviewsByHotel(@PathVariable Long hotelId) {
        List<Review> reviews = reviewRepository.findByHotelId(hotelId);
        List<ReviewWithGradesDTO> dtos = new ArrayList<>();
        for (Review r : reviews) dtos.add(new ReviewWithGradesDTO(r));
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/latest-overall-by-provider/{hotelId}")
    public ResponseEntity<List<OverallByProviderDTO>> getLatestOverallByProvider(@PathVariable Long hotelId) {
        List<OverallByProvider> all = overallByProviderRepository.findByReview_Hotel_IdOrderByProviderIdAscCreatedAtDesc(hotelId);
        // Group by provider, pick latest per provider
        Map<Long, OverallByProviderDTO> latestByProvider = new LinkedHashMap<>();
        for (OverallByProvider obp : all) {
            Long providerId = obp.getProvider() != null ? obp.getProvider().getId() : null;
            if (providerId != null && !latestByProvider.containsKey(providerId)) {
                latestByProvider.put(providerId, new OverallByProviderDTO(obp));
            }
        }
        return ResponseEntity.ok(new ArrayList<>(latestByProvider.values()));
    }
} 