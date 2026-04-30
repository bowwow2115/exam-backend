# AGENT.md

## Project Overview

This is a Spring Boot 4 / Java 17 web application for multiple-choice exams with multiple correct answers. It uses Spring Data JPA, Spring Security, and PostgreSQL in production. Tests use H2 in PostgreSQL compatibility mode.

Main features:
- Account signup and HTTP Basic authentication
- Exam creation and public exam lookup
- Multiple-answer question submission and scoring
- Attempt result lookup
- Wrong-note creation, lookup, and update
- Optional JSON exam import for authorized local question data

## Tech Stack

- Java 17
- Spring Boot 4.0.6
- Spring Web MVC
- Spring Data JPA
- QueryDSL
- Spring Security
- PostgreSQL
- Gradle
- JUnit 5 / Spring Boot Test

## Important Commands

Run tests:

```bash
env GRADLE_USER_HOME=/tmp/gradle sh gradlew test
```

Run the app after PostgreSQL is available:

```bash
DB_URL=jdbc:postgresql://localhost:5432/exam \
DB_USERNAME=exam \
DB_PASSWORD=exam \
sh gradlew bootRun
```

Default app configuration is in `src/main/resources/application.properties`.
Test configuration is in `src/test/resources/application.properties`.

## Package Structure

- `com.psh.exam.account`: account entity, signup, profile API
- `com.psh.exam.security`: Spring Security configuration and user details
- `com.psh.exam.config`: shared infrastructure configuration such as QueryDSL
- `com.psh.exam.exam`: exam, question, choice domain and exam API
- `com.psh.exam.attempt`: attempt, answer, grading, result API
- `com.psh.exam.wrongnote`: wrong-note domain and API
- `com.psh.exam.common`: shared base entity and exception handling

## Domain Notes

- `Exam` has many `Question` records.
- `Question` has many `Choice` records.
- A question supports multiple correct answers through `Choice.correct`.
- `ExamAttempt` stores one submitted exam result per attempt.
- `AttemptAnswer` stores selected choices for each question.
- Scoring is all-or-nothing per question: selected choice IDs must exactly match correct choice IDs.
- `WrongNote` is unique per account and question.
- A wrong answer creates or updates a wrong note.
- A later correct answer marks the existing wrong note as resolved.

## API Summary

Public:
- `POST /api/accounts/signup`
- `GET /api/exams`
- `GET /api/exams/{examId}`

Authenticated:
- `POST /api/accounts/login`
- `GET /api/accounts/me`
- `POST /api/exams`
- `POST /api/exams/{examId}/attempts`
- `GET /api/attempts/{attemptId}`
- `GET /api/wrong-notes`
- `PATCH /api/wrong-notes/{noteId}`

Authentication currently uses stateless HTTP Basic. If token login is added later, keep the service layer independent from the authentication transport.

## Development Guidelines

- Keep controllers thin. Put validation and business rules in services.
- Use DTO records for request and response payloads.
- Do not expose correct answers from public exam lookup.
- Do not import, translate, or store third-party question banks unless the user has provided data they own or have permission to use.
- Return correct answers and explanations only in attempt results and wrong notes.
- Keep JPA relationships lazy by default, then use `@EntityGraph` on repository queries where read models need related data.
- Use QueryDSL for dynamic conditions or read queries that become awkward with derived repository method names.
- Preserve `spring.jpa.open-in-view=false`; fetch required associations inside service transactions.
- Do not add unrelated refactors while implementing a feature.
- Add focused integration tests for flows that touch grading, persistence, or security.

## Database Configuration

Production defaults:
- `DB_URL`: `jdbc:postgresql://localhost:5432/exam`
- `DB_USERNAME`: `exam`
- `DB_PASSWORD`: `exam`
- `DDL_AUTO`: `update`

For real deployments, prefer Flyway or Liquibase migrations over Hibernate `ddl-auto=update`.

## Exam Import

Authorized local question data can be imported on startup by setting `app.import.exam-json` or the equivalent `APP_IMPORT_EXAM_JSON` environment variable. See `docs/exam-import.md` for the JSON format.
