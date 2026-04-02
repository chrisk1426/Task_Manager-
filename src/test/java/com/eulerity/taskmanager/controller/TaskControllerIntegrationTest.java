package com.eulerity.taskmanager.controller;

import com.eulerity.taskmanager.dto.TaskSuggestion;
import com.eulerity.taskmanager.model.Priority;
import com.eulerity.taskmanager.model.Status;
import com.eulerity.taskmanager.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the full Spring stack: controller → service → repository → H2.
 *
 * @SpringBootTest loads the complete application context including JPA and H2.
 * @AutoConfigureMockMvc provides MockMvc for dispatching HTTP requests without
 * starting a real TCP server — requests are processed in-process.
 *
 * AiService is replaced with a @MockBean so the AI tests never need a real
 * ANTHROPIC_API_KEY — we control exactly what AiService returns in each test.
 *
 * Test isolation: @BeforeEach clears the database (via TaskRepository.deleteAll())
 * so each test starts with an empty tasks table. This is simpler and more
 * reliable than @DirtiesContext (which restarts the full context for every test).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Replaces the real AiService bean with a Mockito mock for the duration
     * of these tests. This prevents any attempt to call the Anthropic API.
     */
    @MockBean
    private AiService aiService;

    @Autowired
    private com.eulerity.taskmanager.repository.TaskRepository taskRepository;

    /** Wipe all rows before each test to guarantee a clean state. */
    @BeforeEach
    void clearDatabase() {
        taskRepository.deleteAll();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Convenience method: creates a task via POST /tasks and returns its
     * auto-generated id. Used by tests that need a pre-existing task.
     */
    private Long createTestTask(String title) throws Exception {
        String body = """
                {"title":"%s","description":"Test desc","dueDate":"2025-06-01","priority":"MEDIUM","status":"TODO"}
                """.formatted(title);

        MvcResult result = mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                           .get("id").asLong();
    }

    // =========================================================================
    // POST /tasks — create task
    // =========================================================================

    /**
     * Happy path: POST /tasks with a valid body returns 201 Created and the
     * saved task JSON including the auto-generated id.
     */
    @Test
    void createTask_returns201WithBody() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Buy milk","description":"Whole milk","dueDate":"2025-05-01","priority":"LOW","status":"TODO"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Buy milk"))
                .andExpect(jsonPath("$.description").value("Whole milk"))
                .andExpect(jsonPath("$.dueDate").value("2025-05-01"))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    /**
     * Validation: blank title triggers @NotBlank and returns 400.
     * The error JSON body must mention "title".
     */
    @Test
    void createTask_returns400WhenTitleBlank() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","dueDate":"2025-05-01","priority":"LOW","status":"TODO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("title")));
    }

    /** Validation: missing dueDate triggers @NotNull and returns 400. */
    @Test
    void createTask_returns400WhenDueDateMissing() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Some task","priority":"LOW","status":"TODO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** Validation: missing priority triggers @NotNull and returns 400. */
    @Test
    void createTask_returns400WhenPriorityMissing() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Some task","dueDate":"2025-05-01","status":"TODO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /** Validation: missing status triggers @NotNull and returns 400. */
    @Test
    void createTask_returns400WhenStatusMissing() throws Exception {
        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Some task","dueDate":"2025-05-01","priority":"HIGH"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // GET /tasks — list all tasks
    // =========================================================================

    /** Happy path: empty database returns 200 with an empty array []. */
    @Test
    void getAllTasks_returnsEmptyArrayInitially() throws Exception {
        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    /** Happy path: two created tasks both appear in the list with correct fields. */
    @Test
    void getAllTasks_returnsCreatedTasks() throws Exception {
        createTestTask("First task");
        createTestTask("Second task");

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("First task"))
                .andExpect(jsonPath("$[1].title").value("Second task"));
    }

    // =========================================================================
    // GET /tasks/{id} — get single task
    // =========================================================================

    /** Happy path: GET by a known id returns 200 and the correct task fields. */
    @Test
    void getTaskById_returns200WhenFound() throws Exception {
        Long id = createTestTask("Fetch me");

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Fetch me"));
    }

    /**
     * Error path: GET with an id that doesn't exist returns 404 with a
     * JSON error body in our standard { status, message } shape.
     */
    @Test
    void getTaskById_returns404WhenNotFound() throws Exception {
        mockMvc.perform(get("/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    // =========================================================================
    // PUT /tasks/{id} — update task
    // =========================================================================

    /** Happy path: PUT replaces all fields and returns the updated task. */
    @Test
    void updateTask_returns200WithUpdatedFields() throws Exception {
        Long id = createTestTask("Original title");

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated title","description":"New desc","dueDate":"2025-12-31","priority":"HIGH","status":"IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.description").value("New desc"))
                .andExpect(jsonPath("$.dueDate").value("2025-12-31"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    /** Error path: PUT on a non-existent id returns 404. */
    @Test
    void updateTask_returns404WhenNotFound() throws Exception {
        mockMvc.perform(put("/tasks/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Title","dueDate":"2025-05-01","priority":"LOW","status":"TODO"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /** Validation: PUT with blank title returns 400. */
    @Test
    void updateTask_returns400WhenRequestInvalid() throws Exception {
        Long id = createTestTask("Valid task");

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","dueDate":"2025-05-01","priority":"LOW","status":"TODO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // =========================================================================
    // DELETE /tasks/{id} — delete task
    // =========================================================================

    /** Happy path: DELETE an existing task returns 204 with no response body. */
    @Test
    void deleteTask_returns204() throws Exception {
        Long id = createTestTask("Delete me");

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        // Verify no response body
        MvcResult result = mockMvc.perform(delete("/tasks/{id}", id + 9999))
                .andReturn();
        assertThat(result.getResponse().getContentLength()).isLessThanOrEqualTo(0);
    }

    /** Error path: DELETE on a non-existent id returns 404. */
    @Test
    void deleteTask_returns404WhenNotFound() throws Exception {
        mockMvc.perform(delete("/tasks/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // =========================================================================
    // Persistence effect tests
    // These verify data survives the full round trip through the HTTP layer,
    // service layer, JPA, and H2 — not just that the service methods work.
    // =========================================================================

    /**
     * Persistence: a task created via POST is immediately retrievable via
     * GET /tasks/{id} with the same id and title.
     */
    @Test
    void createThenGet_taskIsPersisted() throws Exception {
        Long id = createTestTask("Persisted task");

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Persisted task"));
    }

    /**
     * Persistence: after a task is deleted, fetching it by id returns 404 —
     * confirming the row was actually removed from H2, not just marked.
     */
    @Test
    void deleteThenGet_taskIsGone() throws Exception {
        Long id = createTestTask("To be deleted");

        mockMvc.perform(delete("/tasks/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isNotFound());
    }

    /**
     * Persistence: after a task is updated, fetching it reflects the new title
     * — confirming the UPDATE was committed to H2, not just held in memory.
     */
    @Test
    void updateThenGet_changesArePersisted() throws Exception {
        Long id = createTestTask("Before update");

        mockMvc.perform(put("/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"After update","description":null,"dueDate":"2025-06-01","priority":"HIGH","status":"DONE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/tasks/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After update"))
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    // =========================================================================
    // POST /tasks/suggest — AI endpoint
    // =========================================================================

    /**
     * Happy path: POST /tasks/suggest with a valid description calls
     * AiService.suggestTask() and returns the TaskSuggestion as JSON.
     */
    @Test
    void suggestTask_returns200WithSuggestion() throws Exception {
        TaskSuggestion suggestion = new TaskSuggestion(
                "Submit quarterly report",
                "Submit the Q3 report to finance",
                LocalDate.of(2025, 4, 4),
                Priority.HIGH,
                Status.TODO
        );
        when(aiService.suggestTask(anyString())).thenReturn(suggestion);

        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Remind me to submit the quarterly report before Friday\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Submit quarterly report"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.dueDate").value("2025-04-04"));
    }

    /**
     * Validation: blank description triggers @NotBlank on SuggestRequest
     * and returns 400 before AiService is ever called.
     */
    @Test
    void suggestTask_returns400WhenDescriptionBlank() throws Exception {
        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // AiService must never be called for an invalid request
        verify(aiService, never()).suggestTask(anyString());
    }

    /**
     * Error path: when AiService throws 503 (missing API key), the controller
     * propagates it and GlobalExceptionHandler returns 503 with error JSON.
     */
    @Test
    void suggestTask_returns503WhenAiServiceThrows503() throws Exception {
        when(aiService.suggestTask(anyString()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "ANTHROPIC_API_KEY is not configured"));

        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"some task\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ANTHROPIC_API_KEY")));
    }

    /**
     * Error path: when AiService throws 502 (unparseable AI response), the
     * controller propagates it and GlobalExceptionHandler returns 502 with error JSON.
     */
    @Test
    void suggestTask_returns502WhenAiServiceThrows502() throws Exception {
        when(aiService.suggestTask(anyString()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Claude returned a response that could not be parsed"));

        mockMvc.perform(post("/tasks/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"some task\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.message").exists());
    }
}
