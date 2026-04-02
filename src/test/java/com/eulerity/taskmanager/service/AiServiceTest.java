package com.eulerity.taskmanager.service;

import com.eulerity.taskmanager.dto.TaskSuggestion;
import com.eulerity.taskmanager.model.Priority;
import com.eulerity.taskmanager.model.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit tests for AiService.
 *
 * @RestClientTest(AiService.class) starts a minimal Spring context containing
 * only AiService and its dependencies (ObjectMapper, RestClient.Builder).
 * It automatically configures a MockRestServiceServer and binds it to the
 * RestClient.Builder before AiService is constructed — so when AiService
 * calls restClientBuilder.build(), the resulting RestClient intercepts all
 * outbound HTTP calls without hitting the real Anthropic API.
 *
 * ReflectionTestUtils.setField() is used to set the apiKey field directly,
 * bypassing Spring's @Value injection. This lets individual tests control
 * whether the key is blank (to trigger 503) or populated (to make calls).
 */
@RestClientTest(AiService.class)
class AiServiceTest {

    @Autowired
    private AiService aiService;

    @Autowired
    private MockRestServiceServer mockServer;

    private static final String API_URL      = "https://api.anthropic.com/v1/messages";
    private static final String TEST_API_KEY = "test-api-key-12345";

    /**
     * Before each test, inject a non-blank API key so the guard clause in
     * suggestTask() doesn't fire unless the test explicitly sets it to blank.
     */
    @BeforeEach
    void injectApiKey() {
        ReflectionTestUtils.setField(aiService, "apiKey", TEST_API_KEY);
    }

    // =========================================================================
    // Helper: build a mock Anthropic API response
    // =========================================================================

    /**
     * Wraps the given JSON string in the Anthropic Messages API response
     * envelope structure that AiService navigates:
     *   { "content": [ { "type": "text", "text": "<jsonString>" } ] }
     *
     * The inner jsonString is what Claude returns — it is what AiService
     * parses into a TaskSuggestion after extracting content[0].text.
     */
    private String anthropicResponse(String innerJson) {
        // Escape double quotes inside the inner JSON so it is valid inside the outer JSON string
        String escaped = innerJson.replace("\"", "\\\"");
        return "{\"content\":[{\"type\":\"text\",\"text\":\"" + escaped + "\"}]}";
    }

    // =========================================================================
    // Happy path tests
    // =========================================================================

    /**
     * Happy path: suggestTask() calls the Anthropic API, parses the JSON
     * response, and returns a fully-populated TaskSuggestion.
     */
    @Test
    void suggestTask_returnsValidSuggestion() {
        // The JSON Claude is expected to return
        String claudeJson = "{\"title\":\"Submit quarterly report\"," +
                "\"description\":\"Submit the Q3 report to finance\"," +
                "\"dueDate\":\"2025-04-04\"," +
                "\"priority\":\"HIGH\"," +
                "\"status\":\"TODO\"}";

        // Stub the mock server: expect a POST to the Anthropic URL, respond with success
        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withSuccess(anthropicResponse(claudeJson), MediaType.APPLICATION_JSON));

        TaskSuggestion result = aiService.suggestTask("Remind me to submit the Q3 report before Friday");

        assertThat(result.title()).isEqualTo("Submit quarterly report");
        assertThat(result.description()).isEqualTo("Submit the Q3 report to finance");
        assertThat(result.priority()).isEqualTo(Priority.HIGH);
        assertThat(result.status()).isEqualTo(Status.TODO);

        // Verify the mock server received exactly the expected request
        mockServer.verify();
    }

    /**
     * Happy path: stripMarkdownWrapper() is called before parsing. If Claude
     * wraps the JSON in ```json ... ``` despite the prompt instruction,
     * the service should strip the wrapper and still parse correctly.
     */
    @Test
    void suggestTask_stripsMarkdownWrapper() {
        // Claude wraps the JSON in a markdown code block — should still parse
        String claudeJsonWithWrapper =
                "```json\\n" +
                "{\\\"title\\\":\\\"Buy groceries\\\"," +
                "\\\"description\\\":null," +
                "\\\"dueDate\\\":null," +
                "\\\"priority\\\":\\\"LOW\\\"," +
                "\\\"status\\\":\\\"TODO\\\"}\\n```";

        // Build the full Anthropic envelope with the raw (already-escaped) text
        String rawAnthropicResponse = "{\"content\":[{\"type\":\"text\",\"text\":\"" +
                claudeJsonWithWrapper + "\"}]}";

        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withSuccess(rawAnthropicResponse, MediaType.APPLICATION_JSON));

        TaskSuggestion result = aiService.suggestTask("buy groceries");

        assertThat(result.title()).isEqualTo("Buy groceries");
        assertThat(result.priority()).isEqualTo(Priority.LOW);
        assertThat(result.status()).isEqualTo(Status.TODO);

        mockServer.verify();
    }

    // =========================================================================
    // Error path tests — configuration
    // =========================================================================

    /**
     * Error path: suggestTask() throws 503 SERVICE_UNAVAILABLE immediately
     * when the API key is blank, before making any HTTP call.
     */
    @Test
    void suggestTask_throwsServiceUnavailableWhenApiKeyBlank() {
        // Override the key injected in @BeforeEach with a blank value
        ReflectionTestUtils.setField(aiService, "apiKey", "");

        assertThatThrownBy(() -> aiService.suggestTask("any description"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(rse.getReason()).containsIgnoringCase("ANTHROPIC_API_KEY");
                });

        // No HTTP call should have been made — the guard clause fires first
        mockServer.verify();
    }

    // =========================================================================
    // Error path tests — bad API responses
    // =========================================================================

    /**
     * Error path: suggestTask() throws 502 BAD_GATEWAY when the Anthropic API
     * itself returns a 5xx error (e.g., service unavailable on Anthropic's end).
     */
    @Test
    void suggestTask_throwsBadGatewayWhenApiCallFails() {
        // Stub the mock server to return a 500 Internal Server Error
        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withServerError());

        assertThatThrownBy(() -> aiService.suggestTask("any description"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    /**
     * Error path: suggestTask() throws 502 BAD_GATEWAY when Claude returns
     * a plain text response instead of JSON (e.g., "I'm sorry, I can't help
     * with that"). Jackson cannot parse plain text as TaskSuggestion.
     */
    @Test
    void suggestTask_throwsBadGatewayWhenResponseUnparseable() {
        String notJson = "Sorry, I cannot process this request.";
        String anthropicEnvelope = "{\"content\":[{\"type\":\"text\",\"text\":\"" + notJson + "\"}]}";

        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withSuccess(anthropicEnvelope, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> aiService.suggestTask("any description"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    // =========================================================================
    // Error path tests — missing required fields in AI response
    // =========================================================================

    /**
     * Error path: suggestTask() throws 502 when Claude's JSON is parseable
     * but omits the required "title" field. Jackson leaves title as null;
     * validateSuggestion() detects this and throws 502.
     */
    @Test
    void suggestTask_throwsBadGatewayWhenTitleMissing() {
        // Valid JSON but title field is absent
        String missingTitle = "{\"description\":\"Some desc\"," +
                "\"dueDate\":null," +
                "\"priority\":\"HIGH\"," +
                "\"status\":\"TODO\"}";

        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withSuccess(anthropicResponse(missingTitle), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> aiService.suggestTask("any description"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(rse.getReason()).containsIgnoringCase("title");
                });
    }

    /**
     * Error path: suggestTask() throws 502 when the "priority" field is missing.
     */
    @Test
    void suggestTask_throwsBadGatewayWhenPriorityMissing() {
        String missingPriority = "{\"title\":\"Some task\"," +
                "\"description\":null," +
                "\"dueDate\":null," +
                "\"status\":\"TODO\"}";

        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withSuccess(anthropicResponse(missingPriority), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> aiService.suggestTask("any description"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(rse.getReason()).containsIgnoringCase("priority");
                });
    }

    /**
     * Error path: suggestTask() throws 502 when the "status" field is missing.
     */
    @Test
    void suggestTask_throwsBadGatewayWhenStatusMissing() {
        String missingStatus = "{\"title\":\"Some task\"," +
                "\"description\":null," +
                "\"dueDate\":null," +
                "\"priority\":\"MEDIUM\"}";

        mockServer.expect(requestTo(API_URL))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withSuccess(anthropicResponse(missingStatus), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> aiService.suggestTask("any description"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(rse.getReason()).containsIgnoringCase("status");
                });
    }
}
