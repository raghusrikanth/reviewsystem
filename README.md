# Review System Microservice

Review System Microservice that retrieves Agoda.com / Booking.com / Expedia reviews from an AWS S3 bucket, processes the data, and stores it in MySQL.

## **Features**

- Import reviews from AWS S3 or local folder (configurable)
- Concurrent file processing based on threads configured.
- Batch insert for high performance (configurable batch size)
- Error handling and logging (Log4j2)
- Idempotent processing (skips already-processed files), by renaming them to processed
- Configurable scheduler (cron)
- REST API and command-line triggers
- Extensible, normalized MySQL schema
- Docker-ready, all config via environment variables

---

## **Assumptions**

- All data is imported from single source, either same S3 bucket and folder. we can extend this in future if required
- User names are masked in sample file, but in real time we get displayMemberName will be populated or we can get ID for user. For now assumed displayMemberName as unique user.
- Assuming Database will support Unicode.
- We don't need Authentication for now.
- ***

## **Design Decisions**

- Spring Boot & Java 17: Chosen for raid development, but we can do this any other language like python.
- Config-Driven Import: Import source details are in application.yml and can be overwritten using environment variables in container deployment.
- Batch Processing: Reviews, grades, and overall-by-provider records are inserted into the database in configurable batches (default: 25). This improves performance by reducing the number of database round-trips and transaction overhead. If a batch insert fails, the system automatically falls back to inserting records individually, ensuring that a single bad record does not block the import of others. The batch size is configurable via `application.yml` or environment variables, allowing tuning for different database capacities and workloads.
- Master data adding: User table, hotel table and provider table will be populated when ever there is a new Unique displayuserMemberName(should be ID ideally), providerId and hotelID is available.
- Concurrent File Processing: Both local and S3 file imports use a configurable thread pool for parallel processing.
- Locking files: Used renaming files to .processing and then to .processed for supporting multi thread approach. If we stick to S3 as source, better option can be using metadata like tags we can use instead of file renaming, which can fail in edge cases.
- Logging: used Log4j2 as standard logging, currently logging to console and file, with file rotation enabled. In production environment we can move these to cloud watch or any other log aggregators like DataDog or Splunk.
- Unit testing:Core logic is covered by unit tests, with mocking for repositories and configuration.
- Database: Added normalized tabled structure with required PK and FK for querying, added basic indexing, but based on data retrieval needs, we have to extend these.
- System is written to extend in future either as background process service or api based system.
- We can extend this to add Authentication and RBAC in future.ReviewSystems_Sample Outputs.docx

---

## **Quick Start**

### **1. Prerequisites**

- Java 17+
- Maven
- MySQL
- (Optional) Docker

### **2. Database Setup**

- Deploy sschema.sql from resources folder.

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
docker run -p 8089:8089 \
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

Get reviews by-user

```sh
curl -X POST http://localhost:8089/api/reviews/by-user/<userID>
```

Get reviews by-hotel

```sh
curl -X POST http://localhost:8089/api/reviews/by-user/<hotelID>
```

Get overall review latest fpr provider by hoteID

```sh
curl -X POST http://localhost:8089/api/reviews/latest-overall-by-provider/<hotelID>
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

## **CI/CD**

- Added gitlab CI
- This has stages for build, unit test, docker build, and docker deploy
