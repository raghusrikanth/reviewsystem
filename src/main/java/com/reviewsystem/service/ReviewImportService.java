package com.reviewsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsystem.config.JLImportRequiredFieldsConfig;
import com.reviewsystem.config.JLImportS3Config;
import com.reviewsystem.config.JLImportFolderConfig;
import com.reviewsystem.model.*;
import com.reviewsystem.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class ReviewImportService {
    private static final Logger logger = LogManager.getLogger(ReviewImportService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JLImportRequiredFieldsConfig requiredFieldsConfig;
    private final JLImportS3Config s3Config;
    private final JLImportFolderConfig folderConfig;
    @Value("${jlimport.source-aws:false}")
    private boolean sourceAWS;
    @Value("${jlimport.batch-size:50}")
    private int batchSize;

    private final HotelRepository hotelRepository;
    private final ProviderRepository providerRepository;
    private final ReviewerRepository reviewerRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewGradesRepository reviewGradesRepository;
    private final OverallByProviderRepository overallByProviderRepository;

    public void parseAndImportJLFile(String jlFilePath) {
        logger.info("Starting import for file: {}", jlFilePath);
        List<Review> reviewBatch = new java.util.ArrayList<>();
        List<List<ReviewGrades>> gradesBatch = new java.util.ArrayList<>();
        List<OverallByProvider> obpBatch = new java.util.ArrayList<>();
        try (BufferedReader reader = java.nio.file.Files.newBufferedReader(java.nio.file.Paths.get(jlFilePath), StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    JsonNode root = objectMapper.readTree(line);
                    if (!validateRequiredFields(root, lineNumber)) {
                        continue;
                    }
                    // Upsert provider
                    JsonNode comment = root.get("comment");
                    Long providerId = comment.get("providerId").asLong();
                    String providerName = comment.get("reviewProviderText").asText();
                    Provider provider = upsertProvider(providerId, providerName);

                    // Upsert hotel
                    Long hotelId = root.get("hotelId").asLong();
                    String hotelName = root.get("hotelName").isNull() ? null : root.get("hotelName").asText();
                    Hotel hotel = upsertHotel(hotelId, hotelName);

                    // Upsert reviewer
                    JsonNode reviewerInfo = comment.get("reviewerInfo");
                    Reviewer reviewer = upsertReviewer(reviewerInfo);

                    // Upsert review
                    Long reviewId = comment.get("hotelReviewId").asLong();
                    if (reviewRepository.existsById(reviewId)) {
                        logger.info("Line {}: Review {} already exists. Skipping.", lineNumber, reviewId);
                        continue;
                    }
                    Review review = mapReview(comment, reviewId, hotel, provider, reviewer);
                    reviewBatch.add(review);

                    // Review grades and OBP
                    List<ReviewGrades> gradesForThisReview = new java.util.ArrayList<>();
                    if (root.has("overallByProviders")) {
                        for (JsonNode overall : root.get("overallByProviders")) {
                            // OverallByProvider
                            Provider obpProvider = upsertProvider(
                                    overall.get("providerId").asLong(),
                                    overall.get("provider").asText()
                            );
                            OverallByProvider obp = OverallByProvider.builder()
                                    .review(review)
                                    .provider(obpProvider)
                                    .overallScore(overall.get("overallScore").asDouble())
                                    .reviewCount(overall.get("reviewCount").asInt())
                                    .build();
                            obpBatch.add(obp);
                            // Grades
                            if (overall.has("grades")) {
                                Iterator<String> fields = overall.get("grades").fieldNames();
                                while (fields.hasNext()) {
                                    String category = fields.next();
                                    double score = overall.get("grades").get(category).asDouble();
                                    ReviewGrades grade = ReviewGrades.builder()
                                            .review(review)
                                            .category(category)
                                            .score(score)
                                            .build();
                                    gradesForThisReview.add(grade);
                                }
                            }
                        }
                    }
                    gradesBatch.add(gradesForThisReview);

                    // Batch insert if batch size reached
                    if (reviewBatch.size() >= batchSize) {
                        saveBatchWithRetry(reviewBatch, gradesBatch, obpBatch);
                        reviewBatch.clear();
                        gradesBatch.clear();
                        obpBatch.clear();
                    }
                } catch (Exception e) {
                    logger.error("Error processing line {}: {}", lineNumber, e.getMessage());
                }
            }
            // Save any remaining
            if (!reviewBatch.isEmpty()) {
                saveBatchWithRetry(reviewBatch, gradesBatch, obpBatch);
            }
        } catch (Exception e) {
            logger.error("Failed to read JL file: {}", e.getMessage());
        }
        logger.info("Completed import for file: {}", jlFilePath);
    }

    public void parseAndImportJLFolder(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.isDirectory()) {
            logger.error("{} is not a directory.", folderPath);
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".jl") && !name.endsWith("_processed.jl"));
        if (files == null || files.length == 0) {
            logger.info("No new .jl files to be processed in {}", folderPath);
            return;
        }
        int threads = folderConfig.getConcurrentThreads();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (File file : files) {
            futures.add(executor.submit(() -> {
                String filePath = file.getAbsolutePath();
                String newName = filePath.replaceFirst("\\.jl$", "_processed.jl");
                String threadName = Thread.currentThread().getName();
                logger.info("[{}] Attempting to pick file for processing: {}", threadName, filePath);
                // Use atomic rename to prevent double processing
                boolean renamed = file.renameTo(new File(newName + ".processing"));
                if (!renamed) {
                    logger.warn("[{}] Could not lock file for processing (maybe already processing?): {}", threadName, filePath);
                    return;
                }
                File processingFile = new File(newName + ".processing");
                try {
                    logger.info("[{}] Picked and processing JL file: {}", threadName, processingFile.getAbsolutePath());
                    parseAndImportJLFile(processingFile.getAbsolutePath());
                    // Rename to _processed.jl after successful processing
                    File finalFile = new File(newName);
                    if (!processingFile.renameTo(finalFile)) {
                        logger.error("[{}] Failed to rename file {} to {} after processing", threadName, processingFile.getAbsolutePath(), finalFile.getAbsolutePath());
                    } else {
                        logger.info("[{}] Renamed file to {}", threadName, finalFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    logger.error("[{}] Failed to process file {}: {}", threadName, processingFile.getAbsolutePath(), e.getMessage());
                    // Optionally, rename back to original if needed
                }
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) { logger.error("Error in file processing thread: {}", e.getMessage()); }
        }
        executor.shutdown();
    }

    public void importJLFiles() {
        if (sourceAWS) {
            logger.info("Importing JL files from AWS S3 bucket: {}", s3Config.getBucket());
            s3ProcessJLFiles();
        } else {
            parseAndImportJLFolder(folderConfig.getFolderPath());
        }
    }

    void s3ProcessJLFiles() {
        String bucket = s3Config.getBucket();
        String prefix = s3Config.getPrefix();
        int threads = folderConfig.getConcurrentThreads();
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new java.util.ArrayList<>();
        try (S3Client s3 = S3Client.builder()
                .region(Region.of(s3Config.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(s3Config.getAccessKey(), s3Config.getSecretKey())
                ))
                .build()) {
            ListObjectsV2Request listReq = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .build();
            ListObjectsV2Response listRes = s3.listObjectsV2(listReq);
            boolean found = false;
            for (S3Object obj : listRes.contents()) {
                String key = obj.key();
                if (key.endsWith(".jl") && !key.endsWith("_processed.jl") && !key.endsWith(".processing")) {
                    found = true;
                    futures.add(executor.submit(() -> {
                        String threadName = Thread.currentThread().getName();
                        logger.info("[{}] Attempting to pick S3 file for processing: {}", threadName, key);
                        String processingKey = key.replaceFirst("\\.jl$", ".processing");
                        // Try to move (rename) the file to .processing as a distributed lock
                        try {
                            CopyObjectRequest copyToProcessing = CopyObjectRequest.builder()
                                .sourceBucket(bucket)
                                .sourceKey(key)
                                .destinationBucket(bucket)
                                .destinationKey(processingKey)
                                .build();
                            s3.copyObject(copyToProcessing);
                            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
                            logger.info("[{}] Renamed S3 file {} to {} for processing", threadName, key, processingKey);
                        } catch (Exception e) {
                            logger.warn("[{}] Could not lock S3 file for processing (maybe already processing?): {}", threadName, key);
                            return;
                        }
                        // Now process the .processing file
                        String tempDirPath = folderConfig.getTempDir();
                        Path tempFile;
                        try {
                            if (tempDirPath != null && !tempDirPath.isBlank()) {
                                File tempDir = new File(tempDirPath);
                                if (!tempDir.exists()) tempDir.mkdirs();
                                tempFile = Files.createTempFile(tempDir.toPath(), "s3jl_", ".jl");
                            } else {
                                tempFile = Files.createTempFile("s3jl_", ".jl");
                            }
                            logger.info("[{}] bukket:  {},  key : {}, prefix: {}", threadName, bucket, processingKey, prefix);
                            GetObjectRequest getReq = GetObjectRequest.builder().bucket(bucket).key(processingKey).build();
                            try (software.amazon.awssdk.core.ResponseInputStream<software.amazon.awssdk.services.s3.model.GetObjectResponse> s3is = s3.getObject(getReq)) {
                                Files.copy(s3is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                            logger.info("[{}] Downloaded S3 file: {} to {} (size: {} bytes)", threadName, processingKey, tempFile, Files.size(tempFile));
                            // Process
                            logger.info("[{}] Picked and processing S3 JL file: {}", threadName, tempFile.toAbsolutePath());
                            parseAndImportJLFile(tempFile.toAbsolutePath().toString());
                            // Rename/move in S3 to _processed.jl
                            String processedKey = key.replaceFirst("\\.jl$", "_processed.jl");
                            CopyObjectRequest copyToProcessed = CopyObjectRequest.builder()
                                .sourceBucket(bucket)
                                .sourceKey(processingKey)
                                .destinationBucket(bucket)
                                .destinationKey(processedKey)
                                .build();
                            s3.copyObject(copyToProcessed);
                            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(processingKey).build());
                            logger.info("[{}] Renamed S3 file {} to {} after processing", threadName, processingKey, processedKey);
                            // Delete temp file
                            Files.deleteIfExists(tempFile);
                        } catch (Exception e) {
                            logger.error("[{}] Error processing S3 JL file {}: {}", threadName, processingKey, e.getMessage());
                            // Optionally, move back to original name or leave as .processing for manual inspection
                        }
                    }));
                }
            }
            if (!found) {
                logger.info("No new .jl files to be processed in S3 bucket {}/{}", bucket, prefix);
            }
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception e) { logger.error("Error in S3 file processing thread: {}", e.getMessage()); }
            }
            executor.shutdown();
        } catch (Exception e) {
            logger.error("Error processing JL files from S3", e);
        }
    }

    @Transactional
    public void processReviewLine(String line, int lineNumber) {
        try {
            JsonNode root = objectMapper.readTree(line);
            if (!validateRequiredFields(root, lineNumber)) {
                return;
            }
            // Upsert provider
            JsonNode comment = root.get("comment");
            Long providerId = comment.get("providerId").asLong();
            String providerName = comment.get("reviewProviderText").asText();
            Provider provider = upsertProvider(providerId, providerName);

            // Upsert hotel
            Long hotelId = root.get("hotelId").asLong();
            String hotelName = root.get("hotelName").isNull() ? null : root.get("hotelName").asText();
            Hotel hotel = upsertHotel(hotelId, hotelName);

            // Upsert reviewer
            JsonNode reviewerInfo = comment.get("reviewerInfo");
            Reviewer reviewer = upsertReviewer(reviewerInfo);

            // Upsert review
            Long reviewId = comment.get("hotelReviewId").asLong();
            if (reviewRepository.existsById(reviewId)) {
                logger.info("Line {}: Review {} already exists. Skipping.", lineNumber, reviewId);
                return;
            }
            Review review = mapReview(comment, reviewId, hotel, provider, reviewer);
            reviewRepository.save(review);

            // Review grades
            if (root.has("overallByProviders")) {
                for (JsonNode overall : root.get("overallByProviders")) {
                    // OverallByProvider
                    Provider obpProvider = upsertProvider(
                            overall.get("providerId").asLong(),
                            overall.get("provider").asText()
                    );
                    OverallByProvider obp = OverallByProvider.builder()
                            .review(review)
                            .provider(obpProvider)
                            .overallScore(overall.get("overallScore").asDouble())
                            .reviewCount(overall.get("reviewCount").asInt())
                            .build();
                    overallByProviderRepository.save(obp);
                    // Grades
                    if (overall.has("grades")) {
                        Iterator<String> fields = overall.get("grades").fieldNames();
                        while (fields.hasNext()) {
                            String category = fields.next();
                            double score = overall.get("grades").get(category).asDouble();
                            ReviewGrades grade = ReviewGrades.builder()
                                    .review(review)
                                    .category(category)
                                    .score(score)
                                    .build();
                            reviewGradesRepository.save(grade);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing line {}: {}", lineNumber, e.getMessage());
        }
    }

    private boolean validateRequiredFields(JsonNode root, int lineNumber) {
        StringBuilder missing = new StringBuilder();
        for (String field : requiredFieldsConfig.getTopLevel()) {
            if (!root.hasNonNull(field)) missing.append(field).append(", ");
        }
        if (missing.length() > 0) {
            logger.error("Line {}: Missing required fields: {} Skipping.", lineNumber, missing);
            return false;
        }
        JsonNode comment = root.get("comment");
        for (String field : requiredFieldsConfig.getComment()) {
            if (!comment.hasNonNull(field)) missing.append("comment.").append(field).append(", ");
        }
        if (missing.length() > 0) {
            logger.error("Line {}: Missing required fields: {} Skipping.", lineNumber, missing);
            return false;
        }
        return true;
    }

    private Provider upsertProvider(Long id, String name) {
        Optional<Provider> existing = providerRepository.findById(id);
        if (existing.isPresent()) return existing.get();
        Provider provider = Provider.builder().id(id).name(name).build();
        return providerRepository.save(provider);
    }

    private Hotel upsertHotel(Long id, String name) {
        Optional<Hotel> existing = hotelRepository.findById(id);
        if (existing.isPresent()) return existing.get();
        Hotel hotel = Hotel.builder().id(id).name(name).build();
        return hotelRepository.save(hotel);
    }

    private Reviewer upsertReviewer(JsonNode reviewerInfo) {
        String displayName = reviewerInfo.hasNonNull("displayMemberName") ? reviewerInfo.get("displayMemberName").asText() : null;
        String countryName = reviewerInfo.hasNonNull("countryName") ? reviewerInfo.get("countryName").asText() : null;
        String flagName = reviewerInfo.hasNonNull("flagName") ? reviewerInfo.get("flagName").asText() : null;
        String reviewGroupName = reviewerInfo.hasNonNull("reviewGroupName") ? reviewerInfo.get("reviewGroupName").asText() : null;
        String roomTypeName = reviewerInfo.hasNonNull("roomTypeName") ? reviewerInfo.get("roomTypeName").asText() : null;
        Integer countryId = reviewerInfo.hasNonNull("countryId") ? reviewerInfo.get("countryId").asInt() : null;
        Integer lengthOfStay = reviewerInfo.hasNonNull("lengthOfStay") ? reviewerInfo.get("lengthOfStay").asInt() : null;
        Integer reviewGroupId = reviewerInfo.hasNonNull("reviewGroupId") ? reviewerInfo.get("reviewGroupId").asInt() : null;
        Integer roomTypeId = reviewerInfo.hasNonNull("roomTypeId") ? reviewerInfo.get("roomTypeId").asInt() : null;
        Integer reviewedCount = reviewerInfo.hasNonNull("reviewerReviewedCount") ? reviewerInfo.get("reviewerReviewedCount").asInt() : null;
        Boolean isExpertReviewer = reviewerInfo.hasNonNull("isExpertReviewer") ? reviewerInfo.get("isExpertReviewer").asBoolean() : null;
        Boolean isShowGlobalIcon = reviewerInfo.hasNonNull("isShowGlobalIcon") ? reviewerInfo.get("isShowGlobalIcon").asBoolean() : null;
        Boolean isShowReviewedCount = reviewerInfo.hasNonNull("isShowReviewedCount") ? reviewerInfo.get("isShowReviewedCount").asBoolean() : null;

        // Try to find an existing reviewer by all unique fields (or just displayName/countryName if no id)
        Optional<Reviewer> existing = reviewerRepository.findAll().stream()
                .filter(r -> r.getDisplayName() != null && r.getDisplayName().equals(displayName)
                        && r.getCountryName() != null && r.getCountryName().equals(countryName))
                .findFirst();
        if (existing.isPresent()) return existing.get();
        Reviewer reviewer = Reviewer.builder()
                .displayName(displayName)
                .countryName(countryName)
                .flagName(flagName)
                .reviewGroupName(reviewGroupName)
                .roomTypeName(roomTypeName)
                .countryId(countryId)
                .lengthOfStay(lengthOfStay)
                .reviewGroupId(reviewGroupId)
                .roomTypeId(roomTypeId)
                .reviewedCount(reviewedCount)
                .isExpertReviewer(isExpertReviewer)
                .isShowGlobalIcon(isShowGlobalIcon)
                .isShowReviewedCount(isShowReviewedCount)
                .build();
        return reviewerRepository.save(reviewer);
    }

    private Review mapReview(JsonNode comment, Long reviewId, Hotel hotel, Provider provider, Reviewer reviewer) {
        return Review.builder()
                .id(reviewId)
                .hotel(hotel)
                .provider(provider)
                .reviewer(reviewer)
                .rating(comment.hasNonNull("rating") ? comment.get("rating").asDouble() : null)
                .checkInMonthYear(comment.hasNonNull("checkInDateMonthAndYear") ? comment.get("checkInDateMonthAndYear").asText() : null)
                .encryptedReviewData(comment.hasNonNull("encryptedReviewData") ? comment.get("encryptedReviewData").asText() : null)
                .formattedRating(comment.hasNonNull("formattedRating") ? comment.get("formattedRating").asText() : null)
                .formattedReviewDate(comment.hasNonNull("formattedReviewDate") ? comment.get("formattedReviewDate").asText() : null)
                .ratingText(comment.hasNonNull("ratingText") ? comment.get("ratingText").asText() : null)
                .responderName(comment.hasNonNull("responderName") ? comment.get("responderName").asText() : null)
                .responseDateText(comment.hasNonNull("responseDateText") ? comment.get("responseDateText").asText() : null)
                .responseTranslateSource(comment.hasNonNull("responseTranslateSource") ? comment.get("responseTranslateSource").asText() : null)
                .reviewComments(comment.hasNonNull("reviewComments") ? comment.get("reviewComments").asText() : null)
                .reviewNegatives(comment.hasNonNull("reviewNegatives") ? comment.get("reviewNegatives").asText() : null)
                .reviewPositives(comment.hasNonNull("reviewPositives") ? comment.get("reviewPositives").asText() : null)
                .reviewProviderLogo(comment.hasNonNull("reviewProviderLogo") ? comment.get("reviewProviderLogo").asText() : null)
                .reviewProviderText(comment.hasNonNull("reviewProviderText") ? comment.get("reviewProviderText").asText() : null)
                .reviewTitle(comment.hasNonNull("reviewTitle") ? comment.get("reviewTitle").asText() : null)
                .translateSource(comment.hasNonNull("translateSource") ? comment.get("translateSource").asText() : null)
                .translateTarget(comment.hasNonNull("translateTarget") ? comment.get("translateTarget").asText() : null)
                .reviewDate(comment.hasNonNull("reviewDate") ? parseDate(comment.get("reviewDate").asText()) : null)
                .originalTitle(comment.hasNonNull("originalTitle") ? comment.get("originalTitle").asText() : null)
                .originalComment(comment.hasNonNull("originalComment") ? comment.get("originalComment").asText() : null)
                .formattedResponseDate(comment.hasNonNull("formattedResponseDate") ? comment.get("formattedResponseDate").asText() : null)
                .isShowReviewResponse(comment.hasNonNull("isShowReviewResponse") ? comment.get("isShowReviewResponse").asBoolean() : null)
                .build();
    }

    private LocalDateTime parseDate(String dateStr) {
        try {
            return LocalDateTime.parse(dateStr, ISO_DATE_TIME);
        } catch (Exception e) {
            logger.warn("Failed to parse date: {}", dateStr);
            return null;
        }
    }

    private void saveBatchWithRetry(List<Review> reviewBatch, List<List<ReviewGrades>> gradesBatch, List<OverallByProvider> obpBatch) {
        logger.info("Attempting batch insert for {} reviews...", reviewBatch.size());
        try {
            reviewRepository.saveAll(reviewBatch);
            // Flatten and save grades
            List<ReviewGrades> allGrades = new java.util.ArrayList<>();
            for (List<ReviewGrades> grades : gradesBatch) {
                allGrades.addAll(grades);
            }
            if (!allGrades.isEmpty()) reviewGradesRepository.saveAll(allGrades);
            if (!obpBatch.isEmpty()) overallByProviderRepository.saveAll(obpBatch);
            logger.info("Batch insert successful for {} reviews.", reviewBatch.size());
        } catch (Exception batchEx) {
            logger.error("Batch insert failed for {} reviews, retrying individually: {}", reviewBatch.size(), batchEx.getMessage());
            for (int i = 0; i < reviewBatch.size(); i++) {
                Review review = reviewBatch.get(i);
                try {
                    reviewRepository.save(review);
                } catch (Exception ex) {
                    logger.error("Failed to insert review {}: {}", review.getId(), ex.getMessage());
                    continue;
                }
                // Save grades for this review
                List<ReviewGrades> grades = gradesBatch.get(i);
                for (ReviewGrades grade : grades) {
                    try {
                        reviewGradesRepository.save(grade);
                    } catch (Exception ex) {
                        logger.error("Failed to insert grade for review {}: {}", review.getId(), ex.getMessage());
                    }
                }
                // Save OBP for this review
                for (OverallByProvider obp : obpBatch) {
                    if (obp.getReview().equals(review)) {
                        try {
                            overallByProviderRepository.save(obp);
                        } catch (Exception ex) {
                            logger.error("Failed to insert OBP for review {}: {}", review.getId(), ex.getMessage());
                        }
                    }
                }
            }
        }
    }
} 