# Personal Task Manager

A Java 17 REST API for managing personal tasks, built with Spring Boot and backed by an H2 in-memory database. Includes an AI-powered endpoint that parses plain-language descriptions into structured task objects using the Anthropic Claude API.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| **Java 17** | The only required installation. Download from [Adoptium](https://adoptium.net/) if not installed. |
| **Internet access** | Required on first run — Maven and all dependencies are downloaded automatically. |

No database setup, no build tools, and no environment variables are required. An Anthropic API key is already configured in `application.properties` so the AI endpoint works out of the box. The Maven wrapper (`mvnw`) downloads Maven itself on first run.

---

## Running the Application

```bash
./mvnw spring-boot:run
```

That is the only command needed. Here is what happens when you run it:

1. The Maven wrapper downloads **Maven 3.9.9** to `~/.m2/wrapper/dists/` if not already cached
2. Maven downloads all project dependencies from Maven Central (Spring Boot, H2, Jackson, etc.)
3. The project compiles and Spring Boot starts an embedded Tomcat server
4. The API is available at **`http://localhost:8080`**
5. The UI is available at **`http://localhost:8080`** (served as a static page)

> **Note:** The first run takes approximately 1–2 minutes while Maven downloads dependencies. Subsequent runs start in under 5 seconds because everything is cached locally.

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/tasks` | Create a new task |
| `GET` | `/tasks` | List all tasks |
| `GET` | `/tasks/{id}` | Get a single task by id |
| `PUT` | `/tasks/{id}` | Update a task (full replacement) |
| `DELETE` | `/tasks/{id}` | Delete a task |
| `POST` | `/tasks/suggest` | **AI endpoint** — parse a plain-language description into a structured task |

All endpoints return JSON. Errors are returned in a consistent shape:

```json
{ "status": 404, "message": "Task not found with id: 5" }
```

---

## AI-Powered Endpoint — `POST /tasks/suggest`

### What it does

Accepts a plain-language description of a task and returns a structured `TaskSuggestion` JSON object. The description is sent to the **Anthropic Claude API**, which infers the title, description, due date, priority, and status from the text.

The suggestion is **stateless** — it is not saved to the database. The reviewer can inspect the suggestion and choose to submit it via `POST /tasks` if they want to persist it.

---

### API key

An Anthropic API key is already configured in `application.properties` — no environment variable or additional setup is needed. The AI endpoint works immediately after running `./mvnw spring-boot:run`.

---

### Example request

```bash
curl -X POST http://localhost:8080/tasks/suggest \
  -H "Content-Type: application/json" \
  -d '{"description": "remind me to submit the quarterly report before Friday"}'
```

### Example response

```json
{
  "title": "Submit quarterly report",
  "description": "Submit the quarterly report to the finance team before the Friday deadline",
  "dueDate": "2025-04-04",
  "priority": "HIGH",
  "status": "TODO"
}
```

The `dueDate` is inferred from the description using today's date as a reference. If no date is implied (e.g., *"learn Spanish"*), `dueDate` will be `null`.

The `status` is inferred from context:
- `TODO` — task has not been started
- `IN_PROGRESS` — description implies the task is underway
- `DONE` — description implies the task is already complete

---

## Database

The application uses an **H2 in-memory database**. This means:

- **Data is ephemeral** — all tasks are lost when the application stops. This is intentional for a development/demo project.
- **No setup required** — H2 runs inside the JVM with no external database installation.

### H2 Console

A browser-based SQL console is available while the application is running:

| | |
|---|---|
| **URL** | `http://localhost:8080/h2-console` |
| **JDBC URL** | `jdbc:h2:mem:taskdb` |
| **Username** | `sa` |
| **Password** | *(leave blank)* |

Use this to browse the `TASKS` table and verify data directly.

---

## Deliberate Choices

**Required fields on every task**
When designing the `Task` model, I chose to make `status`, `dueDate`, and `priority` required fields rather than optional ones. The spec listed them without marking them optional, but more importantly I wanted the application to have real structure — a task with no priority or due date is less actionable and less useful to the person managing it. Enforcing these fields at the API boundary (via `@NotNull` on the DTO) ensures the data is always complete and consistent.

**Hardened AI prompt with an explicit example and strict format rules**
Claude needs to return data in a very specific shape for the application to parse it reliably. A loose prompt produces inconsistent results — wrong date formats, lowercase enum values, markdown wrappers around the JSON. I strengthened the prompt in three ways: an explicit schema definition with allowed values for each field, a concrete input/output example showing exactly what the response should look like, and a repeated constraint at the end of the prompt. I also specified the date format explicitly (`YYYY-MM-DD`) because without it Claude would sometimes return dates like `"April 4, 2025"` instead of `"2025-04-04"`, which Jackson cannot deserialize into a `LocalDate`.

**Cross-validated the plan with Cursor**
Before implementing, I cross-validated the project plan with Cursor alongside Claude Code. Cursor surfaced several important improvements I incorporated into the design. The most significant was adding a `@ControllerAdvice` global exception handler — without it, Spring returns inconsistent error formats depending on the exception type (sometimes HTML, sometimes default JSON). The global handler means all errors from all layers flow through one place and always return the same `{ "status", "message" }` shape. This pattern — throwing domain exceptions in the service layer and mapping them to HTTP responses in the advice — keeps the service layer clean and the error contract predictable.

---



```bash
./mvnw test
```

Runs all **38 tests** across three test classes:

| Test class | Type | Tests |
|---|---|---|
| `TaskServiceTest` | Unit (Mockito) | 9 |
| `AiServiceTest` | Unit (MockRestServiceServer) | 8 |
| `TaskControllerIntegrationTest` | Integration (SpringBootTest) | 21 |

Tests use an in-memory H2 database and a mocked AI service. **No `ANTHROPIC_API_KEY` is needed to run the tests.**
