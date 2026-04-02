package com.eulerity.taskmanager.dto;

import com.eulerity.taskmanager.model.Priority;
import com.eulerity.taskmanager.model.Status;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Response DTO representing a structured task suggested by the AI endpoint.
 *
 * Used by:
 *   - POST /tasks/suggest  (returned as the response body)
 *
 * Jackson deserializes Claude's JSON response text directly into this record.
 * The expected JSON shape from Claude is:
 * {
 *   "title":       "Submit quarterly report",
 *   "description": "Submit the Q3 report to the finance team",
 *   "dueDate":     "2025-04-04",   // may be null if no date is implied
 *   "priority":    "HIGH",
 *   "status":      "TODO"
 * }
 *
 * After deserialization, AiService validates the @NotNull fields using
 * Jakarta Validation. If any required field is missing (e.g., Claude
 * returned malformed JSON or omitted a required field), AiService throws
 * a 502 Bad Gateway — indicating the upstream AI returned an unusable
 * response, not that the client sent a bad request.
 *
 * Why dueDate is not @NotNull:
 *   Not all plain-language descriptions contain a date (e.g., "learn Spanish").
 *   Forcing a non-null dueDate would cause a 502 on valid open-ended tasks
 *   or force Claude to invent an arbitrary date — both are bad outcomes.
 *
 * @param title       The suggested task title — required
 * @param description An optional longer description — may be null
 * @param dueDate     The inferred due date — may be null if not implied
 * @param priority    The suggested priority level — required
 * @param status      The suggested status — required (always TODO for new suggestions)
 */
public record TaskSuggestion(

        @NotNull(message = "AI response missing required field: title")
        String title,

        // Optional — Claude may omit this for simple tasks
        String description,

        // Optional — not all task descriptions imply a deadline
        LocalDate dueDate,

        @NotNull(message = "AI response missing required field: priority")
        Priority priority,

        @NotNull(message = "AI response missing required field: status")
        Status status

) {}
