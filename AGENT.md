# AGENT.md

## Project

Spring Boot 4 / Java 17 exam app with Vue 3 frontend submodule at `src/main/resources/frontend`.

Backend packages:
- `account`: signup, login, account profile
- `security`: JWT auth and Spring Security
- `exam`: exam/question/choice/import APIs
- `attempt`: submissions, grading, result reveal
- `wrongnote`: wrong answer review notes

Frontend:
- Vue 3 + Vite + Vuetify + Pinia
- API client: `src/main/resources/frontend/src/api/client.js`

## Commands

Backend tests:

```bash
env GRADLE_USER_HOME=/tmp/gradle sh gradlew test
```

Frontend build:

```bash
cd src/main/resources/frontend
npm run build
```

## Auth Contract

Authentication is JWT bearer token based.

Public APIs:
- `POST /api/accounts/signup`
- `POST /api/accounts/login`

Authenticated APIs:
- `GET /api/accounts/me`
- `GET /api/exams`
- `GET /api/exams/{examId}`
- `POST /api/exams`
- `POST /api/exams/{examId}/attempts`
- `GET /api/attempts/{attemptId}`
- `GET /api/wrong-notes`
- `PATCH /api/wrong-notes/{noteId}`

Token rules:
- Login validates email/password from DB and returns `accessToken`.
- Frontend stores the token and sends `Authorization: Bearer <token>` for authenticated APIs.
- Do not reintroduce HTTP Basic auth.
- When changing `SecurityConfig`, check frontend API usage.
- When changing frontend token/API logic, check backend controllers and security tests.

## Exam Data

Source files:
- `src/main/resources/exam.md`
- `src/main/resources/exam2.txt`

Generated/import data lives in `data/`.

Scripts:
- `scripts/parse_exam2_to_import.py`: parse `exam2.txt` to import JSON with explanations.
- `scripts/retranslate_exam_ko.py`: build translated/import JSON and enrich explanations/rationales.
- `scripts/rebuild_translated_exams.py`: rebuild import JSON and reimport exams.

Rules:
- Questions have `explanation`.
- Choices can have `rationale`.
- Correct answers and explanations must only be revealed after submission/result lookup or in wrong notes.
- Do not expose `correct=true`, `correctChoiceIds`, explanations, or rationales from normal exam detail used for taking exams.

## Development Rules

- Keep controllers thin; put validation/business logic in services.
- Use DTO records for request/response payloads.
- Preserve `spring.jpa.open-in-view=false`; fetch needed associations in service transactions.
- Use focused integration tests for auth, grading, result reveal, imports, and security route changes.
- Backend/API/Security changes must be cross-checked against `src/main/resources/frontend`.
- Frontend API changes must be cross-checked against controllers, DTOs, and `SecurityConfig`.
- Avoid unrelated refactors.
- Do not import or store third-party question data unless the user confirms they have rights to use it.

## Current Notes

- `src/main/resources/application.properties` is gitignored; use env vars for local runtime secrets.
- JWT config keys: `JWT_SECRET`, `JWT_EXPIRATION_SECONDS`.
- Main repo tracks the frontend as a git submodule; commit frontend changes inside the submodule first, then update the main repo submodule pointer.
