package com.eulerity.taskmanager.service;

import com.eulerity.taskmanager.dto.TaskRequest;
import com.eulerity.taskmanager.model.Priority;
import com.eulerity.taskmanager.model.Status;
import com.eulerity.taskmanager.model.Task;
import com.eulerity.taskmanager.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TaskService.
 *
 * Uses Mockito to replace TaskRepository with a controlled fake — no database
 * or Spring context is started. Each test runs in isolation in milliseconds.
 *
 * @Mock creates a Mockito proxy for TaskRepository.
 * @InjectMocks creates a real TaskService and injects the mock repository into it.
 * @ExtendWith(MockitoExtension.class) initializes all @Mock and @InjectMocks fields
 * before each test and validates usage after each test.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService taskService;

    // Shared test fixtures — reused across multiple tests
    private TaskRequest validRequest;
    private Task        savedTask;

    @BeforeEach
    void setUp() {
        // A fully-populated request used by create and update tests
        validRequest = new TaskRequest(
                "Buy groceries",
                "Milk and eggs",
                LocalDate.of(2025, 4, 10),
                Priority.MEDIUM,
                Status.TODO
        );

        // The Task entity that the repository would return after saving
        savedTask = new Task();
        savedTask.setId(1L);
        savedTask.setTitle("Buy groceries");
        savedTask.setDescription("Milk and eggs");
        savedTask.setDueDate(LocalDate.of(2025, 4, 10));
        savedTask.setPriority(Priority.MEDIUM);
        savedTask.setStatus(Status.TODO);
    }

    // =========================================================================
    // create() tests
    // =========================================================================

    /**
     * Happy path: create() maps all request fields onto a new Task entity,
     * calls repository.save(), and returns the saved entity (which has an
     * auto-generated id from the database).
     */
    @Test
    void create_savesAndReturnsTask() {
        // Arrange: repository.save() returns our pre-built savedTask
        when(repository.save(any(Task.class))).thenReturn(savedTask);

        // Act
        Task result = taskService.create(validRequest);

        // Assert: the returned task has the correct field values
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Buy groceries");
        assertThat(result.getDescription()).isEqualTo("Milk and eggs");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 4, 10));
        assertThat(result.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(result.getStatus()).isEqualTo(Status.TODO);

        // Verify save() was called exactly once
        verify(repository).save(any(Task.class));
    }

    // =========================================================================
    // findAll() tests
    // =========================================================================

    /**
     * Happy path: findAll() returns the full list from repository.findAll()
     * without modification.
     */
    @Test
    void findAll_returnsAllTasks() {
        Task second = new Task();
        second.setId(2L);
        second.setTitle("Second task");

        when(repository.findAll()).thenReturn(List.of(savedTask, second));

        List<Task> result = taskService.findAll();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }

    /**
     * Edge case: findAll() returns an empty list (never null) when no tasks exist.
     * An empty list serializes as [] in JSON — callers should never receive null.
     */
    @Test
    void findAll_returnsEmptyList() {
        when(repository.findAll()).thenReturn(List.of());

        List<Task> result = taskService.findAll();

        assertThat(result).isNotNull().isEmpty();
    }

    // =========================================================================
    // findById() tests
    // =========================================================================

    /**
     * Happy path: findById() returns the Task when the repository finds it.
     */
    @Test
    void findById_returnsTaskWhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedTask));

        Task result = taskService.findById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("Buy groceries");
    }

    /**
     * Error path: findById() throws ResponseStatusException with 404 NOT FOUND
     * when the repository returns empty. This is the single place in the
     * codebase where a missing task becomes an HTTP 404.
     */
    @Test
    void findById_throwsNotFoundWhenMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.findById(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(rse.getReason()).contains("999");
                });
    }

    // =========================================================================
    // update() tests
    // =========================================================================

    /**
     * Happy path: update() overwrites every field on the existing task with
     * new values from the request, then calls repository.save() with the
     * modified entity.
     */
    @Test
    void update_updatesAllFieldsAndSaves() {
        TaskRequest updateRequest = new TaskRequest(
                "Updated title",
                "Updated description",
                LocalDate.of(2025, 12, 31),
                Priority.HIGH,
                Status.IN_PROGRESS
        );

        // Pre-existing task that will be fetched and then updated
        when(repository.findById(1L)).thenReturn(Optional.of(savedTask));

        // After save, return a task reflecting the updated values
        Task updatedTask = new Task();
        updatedTask.setId(1L);
        updatedTask.setTitle("Updated title");
        updatedTask.setDescription("Updated description");
        updatedTask.setDueDate(LocalDate.of(2025, 12, 31));
        updatedTask.setPriority(Priority.HIGH);
        updatedTask.setStatus(Status.IN_PROGRESS);
        when(repository.save(any(Task.class))).thenReturn(updatedTask);

        Task result = taskService.update(1L, updateRequest);

        // Assert all fields reflect the update
        assertThat(result.getTitle()).isEqualTo("Updated title");
        assertThat(result.getDescription()).isEqualTo("Updated description");
        assertThat(result.getDueDate()).isEqualTo(LocalDate.of(2025, 12, 31));
        assertThat(result.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(result.getStatus()).isEqualTo(Status.IN_PROGRESS);

        verify(repository).save(any(Task.class));
    }

    /**
     * Error path: update() throws 404 immediately when the task doesn't exist,
     * without attempting to save anything.
     */
    @Test
    void update_throwsNotFoundWhenMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(999L, validRequest))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // save() must never be called if the task wasn't found
        verify(repository, never()).save(any());
    }

    // =========================================================================
    // delete() tests
    // =========================================================================

    /**
     * Happy path: delete() confirms the task exists via findById(), then
     * calls deleteById(). No return value is checked — void method.
     */
    @Test
    void delete_callsDeleteByIdWhenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(savedTask));

        taskService.delete(1L);

        // Verify the actual delete was called
        verify(repository).deleteById(1L);
    }

    /**
     * Error path: delete() throws 404 when the task doesn't exist and
     * never calls deleteById() — without this check, deleteById() would
     * silently do nothing, making it impossible to distinguish "deleted"
     * from "never existed".
     */
    @Test
    void delete_throwsNotFoundWhenMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.delete(999L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // deleteById() must never be called if the task wasn't found
        verify(repository, never()).deleteById(any());
    }
}
