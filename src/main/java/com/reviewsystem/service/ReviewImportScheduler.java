package com.reviewsystem.service;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
public class ReviewImportScheduler {
    private static final Logger logger = LogManager.getLogger(ReviewImportScheduler.class);
    private final ReviewImportService reviewImportService;

    @Value("${jlimport.schedule-enabled:true}")
    private boolean scheduleEnabled;

    @Value("${jlimport.schedule-cron:0 0/5 * * * ?}")
    private String scheduleCron;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // The cron expression is injected but must be hardcoded in the annotation; workaround below
    @Scheduled(cron = "${jlimport.schedule-cron:0 0/5 * * * ?}")
    public void scheduledImport() {
        if (!scheduleEnabled) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            logger.info("Scheduled import skipped: previous import still running.");
            return;
        }
        try {
            logger.info("Scheduled import started.");
            reviewImportService.importJLFiles();
            logger.info("Scheduled import finished.");
        } catch (Exception e) {
            logger.error("Scheduled import failed: {}", e.getMessage());
        } finally {
            running.set(false);
        }
    }
} 