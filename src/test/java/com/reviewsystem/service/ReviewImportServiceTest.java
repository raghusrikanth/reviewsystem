package com.reviewsystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reviewsystem.config.JLImportRequiredFieldsConfig;
import com.reviewsystem.config.JLImportS3Config;
import com.reviewsystem.config.JLImportFolderConfig;
import com.reviewsystem.model.*;
import com.reviewsystem.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewImportServiceTest {
    @Mock HotelRepository hotelRepository;
    @Mock ProviderRepository providerRepository;
    @Mock ReviewerRepository reviewerRepository;
    @Mock ReviewRepository reviewRepository;
    @Mock ReviewGradesRepository reviewGradesRepository;
    @Mock OverallByProviderRepository overallByProviderRepository;
    @Mock JLImportRequiredFieldsConfig requiredFieldsConfig;
    @Mock JLImportS3Config s3Config;
    @Mock JLImportFolderConfig folderConfig;

    @InjectMocks ReviewImportService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Default required fields
        when(requiredFieldsConfig.getTopLevel()).thenReturn(List.of("hotelId", "hotelName", "comment"));
        when(requiredFieldsConfig.getComment()).thenReturn(List.of("hotelReviewId", "providerId", "rating", "reviewComments", "reviewDate", "reviewerInfo"));
        // Default folder path
        when(folderConfig.getFolderPath()).thenReturn("/tmp");
    }

    @Test
    void testValidateRequiredFields_allFieldsPresent_returnsTrue() throws Exception {
        String json = "{" +
                "\"hotelId\":1,\"hotelName\":\"Test\",\"comment\":{\"hotelReviewId\":1,\"providerId\":1,\"rating\":5,\"reviewComments\":\"Good\",\"reviewDate\":\"2025-01-01T00:00:00+00:00\",\"reviewerInfo\":{}}}";
        JsonNode node = new ObjectMapper().readTree(json);
        boolean result = ReflectionTestUtils.invokeMethod(service, "validateRequiredFields", node, 1);
        assertTrue(result);
    }

    @Test
    void testValidateRequiredFields_missingTopLevelField_returnsFalse() throws Exception {
        String json = "{" +
                "\"hotelName\":\"Test\",\"comment\":{\"hotelReviewId\":1,\"providerId\":1,\"rating\":5,\"reviewComments\":\"Good\",\"reviewDate\":\"2025-01-01T00:00:00+00:00\",\"reviewerInfo\":{}}}";
        JsonNode node = new ObjectMapper().readTree(json);
        boolean result = ReflectionTestUtils.invokeMethod(service, "validateRequiredFields", node, 1);
        assertFalse(result);
    }

    @Test
    void testValidateRequiredFields_missingCommentField_returnsFalse() throws Exception {
        String json = "{" +
                "\"hotelId\":1,\"hotelName\":\"Test\",\"comment\":{\"providerId\":1,\"rating\":5,\"reviewComments\":\"Good\",\"reviewDate\":\"2025-01-01T00:00:00+00:00\",\"reviewerInfo\":{}}}";
        JsonNode node = new ObjectMapper().readTree(json);
        boolean result = ReflectionTestUtils.invokeMethod(service, "validateRequiredFields", node, 1);
        assertFalse(result);
    }

    @Test
    void testUpsertProvider_insertsIfNotExists() {
        when(providerRepository.findById(1L)).thenReturn(Optional.empty());
        when(providerRepository.save(any())).thenReturn(new Provider());
        Provider result = ReflectionTestUtils.invokeMethod(service, "upsertProvider", 1L, "Test");
        assertNotNull(result);
        verify(providerRepository).save(any());
    }

    @Test
    void testUpsertProvider_returnsExisting() {
        Provider existing = new Provider();
        when(providerRepository.findById(1L)).thenReturn(Optional.of(existing));
        Provider result = ReflectionTestUtils.invokeMethod(service, "upsertProvider", 1L, "Test");
        assertEquals(existing, result);
    }

    @Test
    void testUpsertHotel_insertsIfNotExists() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.empty());
        when(hotelRepository.save(any())).thenReturn(new Hotel());
        Hotel result = ReflectionTestUtils.invokeMethod(service, "upsertHotel", 1L, "Test");
        assertNotNull(result);
        verify(hotelRepository).save(any());
    }

    @Test
    void testUpsertHotel_returnsExisting() {
        Hotel existing = new Hotel();
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(existing));
        Hotel result = ReflectionTestUtils.invokeMethod(service, "upsertHotel", 1L, "Test");
        assertEquals(existing, result);
    }

    @Test
    void testUpsertReviewer_insertsIfNotExists() throws Exception {
        when(reviewerRepository.findAll()).thenReturn(List.of());
        when(reviewerRepository.save(any())).thenReturn(new Reviewer());
        String json = "{\"displayMemberName\":\"A\",\"countryName\":\"B\"}";
        JsonNode node = new ObjectMapper().readTree(json);
        Reviewer result = ReflectionTestUtils.invokeMethod(service, "upsertReviewer", node);
        assertNotNull(result);
        verify(reviewerRepository).save(any());
    }

    @Test
    void testUpsertReviewer_returnsExisting() throws Exception {
        Reviewer existing = Reviewer.builder().displayName("A").countryName("B").build();
        when(reviewerRepository.findAll()).thenReturn(List.of(existing));
        String json = "{\"displayMemberName\":\"A\",\"countryName\":\"B\"}";
        JsonNode node = new ObjectMapper().readTree(json);
        Reviewer result = ReflectionTestUtils.invokeMethod(service, "upsertReviewer", node);
        assertEquals(existing, result);
    }

    @Test
    void testMapReview_mapsFieldsCorrectly() throws Exception {
        Hotel hotel = new Hotel();
        Provider provider = new Provider();
        Reviewer reviewer = new Reviewer();
        String json = "{\"hotelReviewId\":1,\"providerId\":1,\"rating\":5,\"reviewComments\":\"Good\",\"reviewDate\":\"2025-01-01T00:00:00+00:00\",\"reviewerInfo\":{}}";
        JsonNode node = new ObjectMapper().readTree(json);
        Review review = ReflectionTestUtils.invokeMethod(service, "mapReview", node, 1L, hotel, provider, reviewer);
        assertEquals(1L, review.getId());
        assertEquals(hotel, review.getHotel());
        assertEquals(provider, review.getProvider());
        assertEquals(reviewer, review.getReviewer());
        assertEquals(5.0, review.getRating());
        assertEquals("Good", review.getReviewComments());
    }

    @Test
    void testImportJLFiles_usesS3OrLocalBasedOnFlag() {
        // S3 scenario
        ReviewImportService s3Spy = spy(service);
        ReflectionTestUtils.setField(s3Spy, "sourceAWS", true);
        doNothing().when(s3Spy).s3ProcessJLFiles();
        s3Spy.importJLFiles();
        verify(s3Spy).s3ProcessJLFiles();

        // Local scenario
        ReviewImportService localSpy = spy(service);
        ReflectionTestUtils.setField(localSpy, "sourceAWS", false);
        doNothing().when(localSpy).parseAndImportJLFolder(any());
        localSpy.importJLFiles();
        verify(localSpy).parseAndImportJLFolder("/tmp");
    }
} 