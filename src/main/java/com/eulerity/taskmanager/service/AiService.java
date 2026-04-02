package com.eulerity.taskmanager.service;

import com.eulerity.taskmanager.dto.TaskSuggestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for calling the Anthropic Claude API to generate
 * structured task suggestions from plain-language descriptions.
 *
 * This service treats Claude as an untrusted external dependency — every
 * failure mode is explicitly handled:
 *   - 503 Service Unavailable: ANTHROPIC_API_KEY is not configured
 *   - 502 Bad Gateway:         Claude returned a response we cannot use
 *                              (network error, malformed JSON, missing fields)
 *
 * The distinction matters to the caller: 503 means "fix your configuration",
 * 502 means "the upstream service returned something unusable — try again".
 */
@Service
public class AiService {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";
    private static final String CLAUDE_MODEL       = "claude-opus-4-6";

    /**
     * Reads the Anthropic API key from the ANTHROPIC_API_KEY environment
     * variable. The `:` after the property name means it defaults to an
     * empty string if the variable is not set — this allows the application
     * to start and serve CRUD endpoints even without the key configured.
     * Only the /tasks/suggest endpoint will fail (with a clear 503) if the
     * key is missing.
     */
    @Value("${ANTHROPIC_API_KEY:}")
    private String apiKey;

    /**
     * Spring Boot's auto-configured ObjectMapper, injected here for
     * deserializing Claude's JSON response into a TaskSuggestion record.
     * The auto-configured instance has JavaTimeModule registered, which
     * handles LocalDate deserialization from "YYYY-MM-DD" strings.
     */
    private final ObjectMapper objectMapper;

    /**
     * RestClient is Spring 6's modern HTTP client. A single instance is
     * reused across all requests — it is thread-safe and stateless.
     *
     * We accept a RestClient.Builder rather than calling RestClient.create()
     * directly so that tests can bind MockRestServiceServer to the same builder
     * before it is built. Spring Boot auto-configures and injects this builder.
     */
    private final RestClient restClient;

    public AiService(ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        // Build the RestClient from the injected builder. In tests, the builder
        // has already been configured by MockRestServiceServer to intercept calls.
        this.restClient = restClientBuilder.build();
    }

    /**
     * Calls the Claude API with a plain-language description and returns
     * a structured TaskSuggestion parsed from the model's response.
     *
     * The method follows five sequential steps:
     *   1. Guard: reject immediately if API key is not configured
     *   2. Build: construct the HTTP request body with system prompt + user message
     *   3. Call:  POST to the Anthropic API, catching any HTTP/network failure
     *   4. Clean: extract and strip the response text of any markdown wrappers
     *   5. Parse: deserialize JSON into TaskSuggestion and validate required fields
     *
     * @param description a plain-language task description from the user
     * @return a validated TaskSuggestion parsed from Claude's response
     * @throws ResponseStatusException 503 if ANTHROPIC_API_KEY is not set
     * @throws ResponseStatusException 502 if Claude returns an unusable response
     */
    public TaskSuggestion suggestTask(String description) {

        // ------------------------------------------------------------------
        // Step 1: Guard clause — fail fast if the API key is not configured.
        // Returning 503 (Service Unavailable) signals a configuration problem
        // on the server side, not a bad request from the client.
        // ------------------------------------------------------------------
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "ANTHROPIC_API_KEY is not configured. Set this environment " +
                    "variable to enable the AI suggestion endpoint.");
        }

        // ------------------------------------------------------------------
        // Step 2: Build the request body.
        // The Anthropic Messages API expects:
        //   { model, max_tokens, system, messages: [{role, content}] }
        // We use Map.of() for the immutable top-level structure and List.of()
        // for the messages array.
        // ------------------------------------------------------------------
        String systemPrompt = buildSystemPrompt();

        Map<String, Object> requestBody = Map.of(
                "model",      CLAUDE_MODEL,
                "max_tokens", 1024,
                "system",     systemPrompt,
                "messages",   List.of(
                        Map.of("role", "user", "content", description)
                )
        );

        // ------------------------------------------------------------------
        // Step 3: Call the Claude API.
        // Any failure here (network timeout, 4xx/5xx from Anthropic) is
        // caught and rethrown as 502 Bad Gateway — the upstream service
        // failed, not the client's request.
        // ------------------------------------------------------------------
        Map<?, ?> apiResponse;
        try {
            apiResponse = restClient.post()
                    .uri(ANTHROPIC_API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to reach Claude API: " + e.getMessage());
        }

        if (apiResponse == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude API returned a null response");
        }

        // ------------------------------------------------------------------
        // Step 4: Extract and clean the response text.
        // Claude's response body has the shape:
        //   { "content": [ { "type": "text", "text": "..." } ] }
        // We navigate to content[0].text, then defensively strip any
        // ```json ... ``` markdown wrappers Claude may have added despite
        // the prompt instruction — LLMs don't always follow instructions
        // perfectly.
        // ------------------------------------------------------------------
        String rawText = extractResponseText(apiResponse);
        String cleanedJson = stripMarkdownWrapper(rawText);

        // ------------------------------------------------------------------
        // Step 5: Parse the JSON and validate required fields.
        // Jackson deserializes the JSON string into a TaskSuggestion record.
        // If parsing fails (bad JSON, wrong types, unrecognized enum values),
        // we catch JsonProcessingException and return 502.
        // After parsing, we manually verify required fields are non-null —
        // Jackson will not throw if Claude simply omitted a field (it just
        // leaves the field null in the record).
        // ------------------------------------------------------------------
        TaskSuggestion suggestion;
        try {
            suggestion = objectMapper.readValue(cleanedJson, TaskSuggestion.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude returned a response that could not be parsed as JSON: "
                    + e.getOriginalMessage());
        }

        validateSuggestion(suggestion);

        return suggestion;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the hardened system prompt sent to Claude.
     *
     * The prompt uses three reinforcement techniques to maximize the chance
     * that Claude returns parseable JSON:
     *   1. Explicit schema definition with field names and allowed values
     *   2. A concrete input/output example demonstrating the exact format
     *   3. A repeated constraint at the end (Claude attends more to
     *      instructions near the end of a prompt)
     *
     * Today's date is injected so Claude can compute relative dates like
     * "next Friday" or "end of month" correctly.
     */
    private String buildSystemPrompt() {
        return """
                You are a task management assistant. Parse the user's plain-language \
                description into a structured task object.

                Return ONLY a raw JSON object — no markdown, no code blocks, no \
                explanation, no preamble. Your entire response must be a single JSON object.

                Use exactly this schema:
                {
                  "title":       "concise task title",
                  "description": "detailed description, or null if not applicable",
                  "dueDate":     "YYYY-MM-DD, or null if no date is implied",
                  "priority":    "LOW, MEDIUM, or HIGH",
                  "status":      "TODO, IN_PROGRESS, or DONE"
                }

                Rules:
                - priority must be exactly one of: LOW, MEDIUM, HIGH (uppercase)
                - status must be exactly one of: TODO, IN_PROGRESS, DONE (uppercase)
                - Infer status from context:
                    - Use TODO if the task has not been started
                    - Use IN_PROGRESS if the description implies it has been started but not finished
                    - Use DONE if the description implies it has already been completed
                    - Default to TODO if status cannot be determined from context
                - dueDate must be in YYYY-MM-DD format or null — never any other date format
                - Today's date is %s. Use this to compute relative dates like "next Friday"

                Example input:  "remind me to submit the quarterly report before Friday"
                Example output: {"title":"Submit quarterly report","description":"Submit the quarterly report to the finance team","dueDate":"%s","priority":"HIGH","status":"TODO"}

                Remember: return only the raw JSON object. No other text before or after it.\
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(4));
    }

    /**
     * Navigates the Anthropic API response structure to extract the text
     * content from Claude's message.
     *
     * Expected response shape:
     *   { "content": [ { "type": "text", "text": "..." } ] }
     *
     * Throws 502 if the structure is missing or malformed.
     */
    @SuppressWarnings("unchecked")
    private String extractResponseText(Map<?, ?> response) {
        List<Map<String, Object>> content;
        try {
            content = (List<Map<String, Object>>) response.get("content");
        } catch (ClassCastException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unexpected structure in Claude API response");
        }

        if (content == null || content.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude API response contained no content");
        }

        Object text = content.get(0).get("text");
        if (text == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude API response content missing 'text' field");
        }

        return text.toString();
    }

    /**
     * Strips markdown code block wrappers from Claude's response text.
     *
     * Despite the prompt instruction to return only raw JSON, Claude
     * occasionally wraps responses in ```json ... ``` or ``` ... ```.
     * This method removes those wrappers defensively so the downstream
     * JSON parser receives clean input.
     *
     * Examples of what gets stripped:
     *   ```json\n{"title": "..."}```  →  {"title": "..."}
     *   ```\n{"title": "..."}```      →  {"title": "..."}
     */
    private String stripMarkdownWrapper(String text) {
        String cleaned = text.trim();

        // Remove opening ```json or ``` fence
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        // Remove closing ``` fence
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Validates that all required fields in the parsed TaskSuggestion are
     * non-null.
     *
     * Jackson does not throw when a JSON field is absent — it silently leaves
     * the record field as null. We check manually here so we can return a
     * clear 502 with the name of the missing field rather than letting a
     * NullPointerException surface somewhere deeper in the call stack.
     *
     * dueDate and description are intentionally not checked — they are
     * optional fields that may legitimately be null.
     */
    private void validateSuggestion(TaskSuggestion suggestion) {
        if (suggestion.title() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude response missing required field: title");
        }
        if (suggestion.priority() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude response missing required field: priority");
        }
        if (suggestion.status() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Claude response missing required field: status");
        }
    }
}
