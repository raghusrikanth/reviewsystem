# Use a lightweight OpenJDK 17 image
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the built jar into the image
COPY target/reviewsystem-0.0.1-SNAPSHOT.jar app.jar
COPY files/agoda_com_2025-04-10_processed.jl /app/files/agoda_com_2025-04-10.jl

# Expose the default Spring Boot port
EXPOSE 8088

# Optionally, allow overriding the active profile and config via env
# You can pass env vars to override application.yml values
# Example: -e JLIMPORT_S3_BUCKET=your-bucket -e JLIMPORT_FOLDER_PATH=/data

# Start the app
ENTRYPOINT ["java", "-jar", "app.jar"] 