package com.eulerity.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body DTO for the AI-powered task suggestion endpoint.
 *
 * Used by:
 *   - POST /tasks/suggest
 *
 * Contains a single plain-language description that Claude will parse
 * into a structured TaskSuggestion. For example:
 *   { "description": "remind me to submit the quarterly report before Friday" }
 *
 * @NotBlank ensures the description is rejected with a 400 Bad Request
 * before the request ever reaches AiService — this avoids making a
 * pointless (and billable) API call to Claude with an empty prompt.
 *
 * @param description A plain-language description of the task to suggest
 */
public record SuggestRequest(

        @NotBlank(message = "Description is required")
        String description

) {}
