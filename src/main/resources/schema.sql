CREATE DATABASE IF NOT EXISTS reviewsystem DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE reviewsystem;

-- Hotel Table
CREATE TABLE hotel (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Provider Table
CREATE TABLE provider (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Reviewer Table
CREATE TABLE reviewer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(100),
    country_name VARCHAR(100),
    flag_name VARCHAR(10),
    review_group_name VARCHAR(100),
    room_type_name VARCHAR(100),
    country_id INT,
    length_of_stay INT,
    review_group_id INT,
    room_type_id INT,
    reviewed_count INT,
    is_expert_reviewer BOOLEAN,
    is_show_global_icon BOOLEAN,
    is_show_reviewed_count BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Review Table
CREATE TABLE review (
    id BIGINT PRIMARY KEY,
    hotel_id BIGINT,
    provider_id BIGINT,
    reviewer_id BIGINT,
    rating DECIMAL(3,1),
    check_in_month_year VARCHAR(50),
    encrypted_review_data VARCHAR(255),
    formatted_rating VARCHAR(10),
    formatted_review_date VARCHAR(50),
    rating_text VARCHAR(50),
    responder_name VARCHAR(255),
    response_date_text VARCHAR(50),
    response_translate_source VARCHAR(10),
    review_comments LONGTEXT,
    review_negatives LONGTEXT,
    review_positives LONGTEXT,
    review_provider_logo VARCHAR(255),
    review_provider_text VARCHAR(100),
    review_title VARCHAR(255),
    translate_source VARCHAR(10),
    translate_target VARCHAR(10),
    review_date DATETIME,
    original_title LONGTEXT,
    original_comment LONGTEXT,
    formatted_response_date VARCHAR(50),
    is_show_review_response BOOLEAN,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotel(id),
    FOREIGN KEY (provider_id) REFERENCES provider(id),
    FOREIGN KEY (reviewer_id) REFERENCES reviewer(id),
    INDEX idx_review_date (review_date)
);

-- Review Grades Table (for flexible rating categories)
CREATE TABLE review_grades (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT,
    category VARCHAR(100),
    score DECIMAL(3,1),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (review_id) REFERENCES review(id)
);

-- Overall By Provider Table
CREATE TABLE overall_by_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT,
    provider_id BIGINT,
    overall_score DECIMAL(3,1),
    review_count INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (review_id) REFERENCES review(id),
    FOREIGN KEY (provider_id) REFERENCES provider(id),
    INDEX idx_overall_by_provider_review_date (created_at)
); 