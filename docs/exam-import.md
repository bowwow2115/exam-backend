# Exam Import

The application can import an exam from a local JSON file on startup. Use this only with question data that you own or have permission to store and transform.

Enable import with:

```bash
APP_IMPORT_EXAM_JSON=./data/exam-import.json sh gradlew bootRun
```

Spring Boot maps the environment variable above to `app.import.exam-json`.

The importer skips an exam when another exam with the same title already exists. Disable that behavior with:

```bash
APP_IMPORT_SKIP_EXISTING_TITLE=false
```

## JSON Format

```json
{
  "creator": {
    "email": "admin@example.com",
    "password": "password123",
    "displayName": "Admin"
  },
  "title": "Authorized Practice Exam",
  "description": "Imported from an authorized local JSON file.",
  "timeLimitMinutes": 130,
  "published": true,
  "questions": [
    {
      "prompt": "A sample question that you are allowed to use.",
      "points": 1,
      "explanation": "Optional explanation.",
      "choices": [
        {
          "text": "First answer",
          "correct": true
        },
        {
          "text": "Second answer",
          "correct": false
        }
      ]
    }
  ]
}
```
