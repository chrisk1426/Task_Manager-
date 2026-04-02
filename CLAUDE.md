---

## Overview

You will build a small Java 17 REST API for a **personal task manager**, using an AI agentic model as your primary development tool. When you submit, you will include both your code and a transcript of your conversation with the model.

The transcript is as important as the code. We want to see how you direct the model, evaluate its output, and recover when things go wrong.

---

## Requirements

### Technical stack
- **Java 17**
- **Spring Boot** (latest stable)
- **Maven or Gradle** (your choice)
- **H2 in-memory database** (no external database setup required)

### Your API must include

**1. Task CRUD endpoints**

A `Task` has at minimum:
- `id` (auto-generated)
- `title` (string, required)
- `description` (string, optional)
- `dueDate` (date)
- `priority` (LOW / MEDIUM / HIGH)
- `status` (TODO / IN_PROGRESS / DONE)

Implement the following endpoints:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/tasks` | Create a new task |
| `GET` | `/tasks` | List all tasks |
| `GET` | `/tasks/{id}` | Get a single task |
| `PUT` | `/tasks/{id}` | Update a task |
| `DELETE` | `/tasks/{id}` | Delete a task |

**2. At least one AI-powered endpoint**

Integrate a call to an AI model (Claude, GPT-4, Gemini, or similar) within your API. You may choose your approach — here are some ideas to get you started, but you are welcome to come up with your own:

- `POST /tasks/suggest` — accepts a plain-language description (e.g., `"remind me to submit the quarterly report before Friday"`) and returns a structured task object
- `POST /tasks/{id}/summarize` — returns a plain-language summary or explanation of a task
- `POST /tasks/{id}/breakdown` — returns a suggested list of subtasks for a complex task

Your AI-powered endpoint should return well-structured JSON. It does not need to persist anything — a stateless call to the model is fine.

**3. A simple UI**

Include a minimal frontend that allows a reviewer to interact with your API without using a REST client. It does not need to be polished or feature-complete — a basic HTML page is fine. It should support:

- Viewing the list of tasks
- Creating a new task
- Triggering your AI-powered endpoint and displaying the result

Styling and aesthetics are not evaluated. The UI exists purely to make your project easier to explore.

**4. Basic tests**

Include a test suite that covers the core behavior of your API. At minimum:

- At least one unit test per service-layer method (happy path)
- At least one integration test that starts the Spring context and exercises each CRUD endpoint end-to-end
- At least one test for your AI-powered endpoint (mocking the external model call is fine and expected)

Tests must pass when running:

```bash
./mvnw test
```

or

```bash
./gradlew test
```

**4. A working README**

Your README (separate from this document) must allow a reviewer to clone your repo, run a single command, and have the API running locally. Include:
- Setup instructions
- How to run the project
- A description of your AI-powered endpoint and example request/response

### Build requirement

Your project must build and start with a single command, for example:

```bash
./mvnw spring-boot:run
```

or

```bash
./gradlew bootRun
```

We will run this command cold, with no prior setup beyond having Java 17 and internet access.

---

## What You Do Not Need to Build

- Authentication or authorization
- A production-grade database
- High test coverage beyond the basics described above
- Deployment configuration

---

Directory Layout: 
```
/
├── src/                    # Your Java source code
├── pom.xml 
├── README.md               # Your own README with setup instructions
└── transcript.md           # Your full AI conversation transcript
```
