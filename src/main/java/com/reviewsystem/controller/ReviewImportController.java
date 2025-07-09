package com.reviewsystem.controller;

import com.reviewsystem.service.ReviewImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewImportController {
    private final ReviewImportService reviewImportService;

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
} 