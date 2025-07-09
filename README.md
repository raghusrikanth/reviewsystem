# Review System Microservice

Review System Microservice that retrieves Agoda.com / Booking.com / Expedia reviews from an AWS S3 bucket, processes the data, and stores it in MySQL.

## **Features**

- Import reviews from AWS S3 or local folder (configurable)
- Batch insert for high performance (configurable batch size)
- Error handling and logging (Log4j2)
- Idempotent processing (skips already-processed files), by renaming them to processed
- Configurable scheduler (cron)
- REST API and command-line triggers
- Extensible, normalized MySQL schema
- Docker-ready, all config via environment variables

---

## **Quick Start**

### **1. Prerequisites**

- Java 17+
- Maven
- MySQL
- (Optional) Docker

### **2. Build the Project**

```sh
mvn clean package
```

### **3. Configure Database and Import**

- Edit `src/main/resources/application.yml` for your DB, S3, and import settings.
- Or, override any config with environment variables (see below).

### **4. Run Locally**

```sh
java -jar target/reviewsystem-0.0.1-SNAPSHOT.jar
```

### **5. Run with Docker**

```sh
docker build -t reviewsystem:latest .
docker run -p 8088:8088 \
  -e SPRING_DATASOURCE_URL="jdbc:mysql://host:3306/db?useSSL=false" \
  -e SPRING_DATASOURCE_USERNAME="user" \
  -e SPRING_DATASOURCE_PASSWORD="pass" \
  -e JLIMPORT_SOURCE_AWS=false \
  -e JLIMPORT_FOLDER_PATH=/data \
  -e JLIMPORT_BATCH_SIZE=50 \
  -e JLIMPORT_SCHEDULE_ENABLED=true \
  -e JLIMPORT_SCHEDULE_CRON="0 0/5 * * * ?" \
  --name reviewsystem \
  reviewsystem:latest
```

---

## **Configuration**

All config can be set in `application.yml` or overridden with environment variables:

| Property                     | Env Variable               | Description                             |
| ---------------------------- | -------------------------- | --------------------------------------- |
| `spring.datasource.url`      | SPRING_DATASOURCE_URL      | JDBC URL for MySQL                      |
| `spring.datasource.username` | SPRING_DATASOURCE_USERNAME | DB username                             |
| `spring.datasource.password` | SPRING_DATASOURCE_PASSWORD | DB password                             |
| `jlimport.source-aws`        | JLIMPORT_SOURCE_AWS        | `true` for S3, `false` for local folder |
| `jlimport.folder-path`       | JLIMPORT_FOLDER_PATH       | Path to local folder for .jl files      |
| `jlimport.s3.bucket`         | JLIMPORT_S3_BUCKET         | S3 bucket name                          |
| `jlimport.s3.region`         | JLIMPORT_S3_REGION         | S3 region                               |
| `jlimport.s3.access-key`     | JLIMPORT_S3_ACCESS_KEY     | S3 access key                           |
| `jlimport.s3.secret-key`     | JLIMPORT_S3_SECRET_KEY     | S3 secret key                           |
| `jlimport.s3.prefix`         | JLIMPORT_S3_PREFIX         | S3 prefix/path                          |
| `jlimport.temp-dir`          | JLIMPORT_TEMP_DIR          | Directory for temp files (S3 downloads) |
| `jlimport.batch-size`        | JLIMPORT_BATCH_SIZE        | Batch size for DB inserts               |
| `jlimport.schedule-enabled`  | JLIMPORT_SCHEDULE_ENABLED  | Enable/disable scheduler                |
| `jlimport.schedule-cron`     | JLIMPORT_SCHEDULE_CRON     | Cron for scheduler (Quartz format)      |

---

## **Usage**

### **REST API**

Import folder endpoint (same as above):

```sh
curl -X POST http://localhost:8088/api/reviews/import-jl-folder
```

### **Command Line**

- Run with any argument to trigger import:
  ```sh
  java -jar target/reviewsystem-0.0.1-SNAPSHOT.jar
  ```

### **Scheduler**

- Runs automatically if `jlimport.schedule-enabled: true`.
- Schedule is set by `jlimport.schedule-cron`.
- Only one import runs at a time (overlapping runs are skipped).

---

## **Batch Processing & Error Handling**

- Imports are processed in batches (configurable size).
- If a batch fails, each record is retried individually and errors are logged.
- Only bad records are skipped; good records are imported.

---

## **Logging**

- Logs to both console and `logs/reviewsystem.log` (Log4j2).
- Batch operations, errors, and import progress are all logged.

---

## **Extending**

- Add new required fields in `application.yml` under `jlimport.required-fields`.
- Add new import sources as needed.
- Schema is extensible for new review fields, providers, or rating systems.

---
