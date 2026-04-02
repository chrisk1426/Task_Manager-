package com.eulerity.taskmanager.controller;

import com.eulerity.taskmanager.dto.SuggestRequest;
import com.eulerity.taskmanager.dto.TaskRequest;
import com.eulerity.taskmanager.dto.TaskSuggestion;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.service.AiService;
import com.eulerity.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the Task Manager API.
 *
 * @RestController combines @Controller and @ResponseBody — every method
 * return value is automatically serialized to JSON and written into the
 * HTTP response body. No @ResponseBody annotation is needed on individual
 * methods.
 *
 * @RequestMapping("/tasks") applies the /tasks prefix to all endpoints
 * in this class so each method only needs to declare the suffix (e.g.,
 * "/{id}" instead of "/tasks/{id}").
 *
 * This class is intentionally thin — it handles HTTP concerns only
 * (routing, request binding, status codes) and delegates all business
 * logic to TaskService and AiService.
 */
@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AiService   aiService;

    /**
     * Constructor injection makes dependencies explicit and allows this
     * class to be instantiated directly in tests without a Spring context.
     * Spring automatically provides the TaskService and AiService beans
     * that were created at application startup.
     */
    public TaskController(TaskService taskService, AiService aiService) {
        this.taskService = taskService;
        this.aiService   = aiService;
    }

    // -------------------------------------------------------------------------
    // POST /tasks — Create a new task
    // -------------------------------------------------------------------------

    /**
     * Creates a new task and returns it with HTTP 201 Created.
     *
     * @Valid triggers Bean Validation on the request body before this method
     * runs. If any constraint fails (e.g., blank title, null dueDate), Spring
     * throws MethodArgumentNotValidException and GlobalExceptionHandler returns
     * a 400 with field-level error messages — this method is never called.
     *
     * ResponseEntity.status(CREATED).body(task) explicitly sets the 201 status.
     * Plain `return task` would default to 200 OK, which is semantically wrong
     * for a resource creation — 201 signals that a new resource was created.
     */
    @PostMapping
    public ResponseEntity<Task> createTask(@Valid @RequestBody TaskRequest request) {
        Task created = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // GET /tasks — List all tasks
    // -------------------------------------------------------------------------

    /**
     * Returns all tasks as a JSON array with HTTP 200 OK.
     *
     * Returns an empty array [] when no tasks exist — never null.
     * Jackson serializes an empty List as [] automatically.
     */
    @GetMapping
    public List<Task> getAllTasks() {
        return taskService.findAll();
    }

    // -------------------------------------------------------------------------
    // GET /tasks/{id} — Get a single task
    // -------------------------------------------------------------------------

    /**
     * Returns a single task by id with HTTP 200 OK.
     *
     * @PathVariable binds the {id} segment of the URL to the id parameter.
     * If no task exists with this id, TaskService throws a
     * ResponseStatusException(404) which GlobalExceptionHandler converts
     * to a JSON error response.
     */
    @GetMapping("/{id}")
    public Task getTaskById(@PathVariable Long id) {
        return taskService.findById(id);
    }

    // -------------------------------------------------------------------------
    // PUT /tasks/{id} — Update an existing task
    // -------------------------------------------------------------------------

    /**
     * Replaces all fields of an existing task with the request values.
     * Returns the updated task with HTTP 200 OK.
     *
     * This is a full replacement (PUT semantics), not a partial update (PATCH).
     * Every field in TaskRequest is required, so the entire task is always
     * rewritten — there is no risk of accidentally nulling out a field.
     *
     * Throws 404 via TaskService if the task does not exist.
     */
    @PutMapping("/{id}")
    public Task updateTask(@PathVariable Long id,
                           @Valid @RequestBody TaskRequest request) {
        return taskService.update(id, request);
    }

    // -------------------------------------------------------------------------
    // DELETE /tasks/{id} — Delete a task
    // -------------------------------------------------------------------------

    /**
     * Deletes a task by id and returns HTTP 204 No Content.
     *
     * 204 No Content is the correct status for a successful DELETE — it signals
     * that the operation succeeded but there is no resource left to return.
     * ResponseEntity<Void> with no body produces an empty response body.
     *
     * Throws 404 via TaskService if the task does not exist.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // POST /tasks/suggest — AI-powered task suggestion
    // -------------------------------------------------------------------------

    /**
     * Accepts a plain-language description and returns a structured task
     * suggestion generated by the Claude AI model.
     *
     * The suggestion is stateless — it is not saved to the database.
     * The client can review the suggestion and choose to call POST /tasks
     * with the returned fields if they want to persist it.
     *
     * Note: this mapping must be declared as a distinct path (/tasks/suggest)
     * rather than a path variable to avoid Spring MVC treating "suggest" as
     * a Long id on the GET /{id} endpoint. Since this uses POST and GET /{id}
     * uses GET, there is no actual conflict — but the explicit path is clearer.
     *
     * Throws 503 if ANTHROPIC_API_KEY is not configured.
     * Throws 502 if Claude returns a response that cannot be parsed.
     * Both are handled by GlobalExceptionHandler.
     */
    @PostMapping("/suggest")
    public TaskSuggestion suggestTask(@Valid @RequestBody SuggestRequest request) {
        return aiService.suggestTask(request.description());
    }
}
