package com.eulerity.taskmanager.service;

import com.eulerity.taskmanager.dto.TaskRequest;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.repository.TaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Service layer for Task CRUD operations.
 *
 * This class contains all business logic for managing tasks. The controller
 * delegates to these methods and knows nothing about the database. The
 * repository knows nothing about HTTP — this service is the bridge between
 * the two layers.
 *
 * All 404 handling is centralized in findById() so the logic is never
 * duplicated. update() and delete() both call findById() internally,
 * meaning a missing task always produces a consistent 404 response
 * regardless of which endpoint triggered the lookup.
 */
@Service
public class TaskService {

    private final TaskRepository repository;

    /**
     * Constructor injection is preferred over @Autowired field injection
     * because it makes dependencies explicit and allows the class to be
     * instantiated directly in unit tests without a Spring context.
     */
    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new task from the given request and persists it.
     *
     * Maps each field from the DTO onto a new Task entity, then calls
     * repository.save(). Spring Data detects that the id is null and
     * issues an INSERT. The returned entity has its auto-generated id
     * populated by the database.
     *
     * @param request the validated request body from the controller
     * @return the persisted Task with its generated id
     */
    public Task create(TaskRequest request) {
        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority());
        task.setStatus(request.status());
        return repository.save(task);
    }

    /**
     * Returns all tasks in the database.
     *
     * Straight pass-through to the repository — no filtering or
     * transformation needed for this endpoint.
     *
     * @return a list of all Task entities, empty list if none exist
     */
    public List<Task> findAll() {
        return repository.findAll();
    }

    /**
     * Returns a single task by id, or throws 404 if not found.
     *
     * repository.findById() returns Optional<Task>. orElseThrow() unwraps
     * the value if present, or throws ResponseStatusException(404) if empty.
     * Spring's @ControllerAdvice catches this exception and returns a
     * consistent JSON error response to the client.
     *
     * This method is the single source of 404 logic — update() and delete()
     * both delegate here so the behavior is never duplicated.
     *
     * @param id the primary key of the task to find
     * @return the Task entity if found
     * @throws ResponseStatusException 404 if no task exists with this id
     */
    public Task findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Task not found with id: " + id));
    }

    /**
     * Updates an existing task with new values from the request.
     *
     * Calls findById() first — if the task doesn't exist, a 404 is thrown
     * immediately and no further work is done. If found, every field is
     * overwritten with the request values (full replacement, not partial
     * patch). repository.save() detects the existing non-null id and issues
     * an UPDATE rather than an INSERT.
     *
     * @param id      the primary key of the task to update
     * @param request the validated request body with new field values
     * @return the updated Task entity
     * @throws ResponseStatusException 404 if no task exists with this id
     */
    public Task update(Long id, TaskRequest request) {
        // Fetch the existing task — throws 404 if not found
        Task task = findById(id);

        // Overwrite all fields with the new values from the request
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setPriority(request.priority());
        task.setStatus(request.status());

        // save() issues UPDATE because the entity already has a non-null id
        return repository.save(task);
    }

    /**
     * Deletes a task by id.
     *
     * Calls findById() first to confirm the task exists — without this,
     * deleteById() would silently do nothing if the id is missing, making
     * it impossible to distinguish "deleted successfully" from "never existed".
     * By verifying existence first, we guarantee a 404 for unknown ids.
     *
     * @param id the primary key of the task to delete
     * @throws ResponseStatusException 404 if no task exists with this id
     */
    public void delete(Long id) {
        // Confirm existence — throws 404 if not found
        findById(id);
        repository.deleteById(id);
    }
}
