package com.eulerity.taskmanager.dto;

import com.eulerity.taskmanager.model.Priority;
import com.eulerity.taskmanager.model.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request body DTO for creating and updating tasks.
 *
 * Used by:
 *   - POST /tasks        (create a new task)
 *   - PUT  /tasks/{id}   (update an existing task)
 *
 * Using a Java record gives us an immutable object with a canonical
 * constructor, getters, equals(), hashCode(), and toString() generated
 * automatically by the compiler — no boilerplate required.
 *
 * Validation annotations (@NotBlank, @NotNull) are enforced by Spring
 * when @Valid is placed on the controller method parameter. Invalid
 * requests are rejected with a 400 Bad Request before reaching the
 * service layer.
 *
 * @param title       The task title — required, may not be null, empty,
 *                    or whitespace-only (@NotBlank covers all three cases)
 * @param description An optional longer description — may be null or empty
 * @param dueDate     The date the task is due — required per the spec
 * @param priority    The urgency level (LOW/MEDIUM/HIGH) — required
 * @param status      The current state (TODO/IN_PROGRESS/DONE) — required
 */
public record TaskRequest(

        @NotBlank(message = "Title is required")
        String title,

        // Optional — no validation constraint
        String description,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @NotNull(message = "Priority is required")
        Priority priority,

        @NotNull(message = "Status is required")
        Status status

) {}
