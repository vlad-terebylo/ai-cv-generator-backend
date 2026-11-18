package org.example.aicvgenerator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.aicvgenerator.config.Timing.NANOS_PER_MILLISECOND;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.example.aicvgenerator.config.GeminiProperties;
import org.example.aicvgenerator.exception.AiServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** Runs the real client against a tiny local HTTP server that plays Google; the answers are opaque text to it. */
class GeminiClientTest {

    private record Reply(int status, String body, long delayMillis) {
        static Reply ok(String body) {
            return new Reply(200, body, 0);
        }

        static Reply status(int status) {
            return new Reply(status, String.format("{\"error\": {\"status\": \"%d\"}}", status), 0);
        }
    }

    private static final Map<String, Object> SCHEMA = Map.of("type", "OBJECT");

    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();
    private final List<String> apiKeys = new CopyOnWriteArrayList<>();
    private volatile IntFunction<Reply> script = attempt -> Reply.ok(answer(true));
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int attempt = requests.incrementAndGet();
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            paths.add(exchange.getRequestURI().getPath());
            apiKeys.add(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            Reply reply = script.apply(attempt);
            if (reply.delayMillis() > 0) {
                try {
                    Thread.sleep(reply.delayMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /** Google's envelope around the structured answer, with the answer itself as a JSON string. */
    private String answer(boolean isCv) {
        Map<String, Object> cv = isCv
                ? Map.of("isCv", true, "firstName", "Valieria", "lastName", "Karlova", "email", "v.karlova@gmail.com",
                        "summary", "proactive", "experience", List.of(Map.of("startYear", 2025, "position",
                                "Senior Java Developer", "company", "Absolute")))
                : Map.of("isCv", false);
        return envelope(json.writeValueAsString(cv));
    }

    private String envelope(String text) {
        return json.writeValueAsString(Map.of("candidates",
                List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))));
    }

    private GeminiClient client(String apiKey, int maxAttempts, Duration readTimeout, Integer thinkingBudget) {
        GeminiProperties properties = new GeminiProperties(apiKey, "gemini-test",
                String.format("http://127.0.0.1:%d", server.getAddress().getPort()), Duration.ofSeconds(2), readTimeout, maxAttempts,
                Duration.ofMillis(10), thinkingBudget);
        return new GeminiClient(properties, RestClient.builder());
    }

    private GeminiClient client() {
        return client("test-key", 3, Duration.ofSeconds(5), 0);
    }

    @Test
    void returnsTheAnswerTextAndSendsTheKeyModelPromptAndSchema() {
        String answer = client().generateJson("Valieria Karlova, Java developer", SCHEMA);

        assertThat(json.readTree(answer).get("firstName").asString()).isEqualTo("Valieria");
        assertThat(paths).containsExactly("/models/gemini-test:generateContent");
        assertThat(apiKeys).containsExactly("test-key");
        assertThat(bodies.get(0)).contains("responseSchema").contains("Valieria Karlova, Java developer");
    }

    @Test
    void asksTheModelNotToThinkWhenTheBudgetIsZero() {
        client("test-key", 3, Duration.ofSeconds(5), 0).generateJson("cv", SCHEMA);

        assertThat(bodies.get(0)).contains("\"thinkingConfig\":{\"thinkingBudget\":0}");
    }

    @Test
    void leavesThinkingAloneWhenNoBudgetIsConfigured() {
        client("test-key", 3, Duration.ofSeconds(5), null).generateJson("cv", SCHEMA);

        assertThat(bodies.get(0)).doesNotContain("thinkingConfig");
    }

    @Test
    void retriesWhenGoogleIsTemporarilyUnavailable() {
        script = attempt -> attempt < 3 ? Reply.status(503) : Reply.ok(answer(true));

        assertThat(client().generateJson("cv", SCHEMA)).contains("Valieria");
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void retriesWhenGoogleSaysTooManyRequests() {
        script = attempt -> attempt < 2 ? Reply.status(429) : Reply.ok(answer(true));

        assertThat(client().generateJson("cv", SCHEMA)).contains("Valieria");
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfAttempts() {
        script = attempt -> Reply.status(503);

        assertThatThrownBy(() -> client().generateJson("cv", SCHEMA)).isInstanceOf(AiServiceException.class)
                .hasMessageContaining("503");

        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryWhenRetriesAreDisabled() {
        script = attempt -> Reply.status(503);

        assertThatThrownBy(() -> client("test-key", 1, Duration.ofSeconds(5), 0).generateJson("cv", SCHEMA))
                .isInstanceOf(AiServiceException.class);

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryErrorsThatRetryingCannotFix() {
        script = attempt -> Reply.status(400);

        assertThatThrownBy(() -> client().generateJson("cv", SCHEMA)).isInstanceOf(AiServiceException.class);

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void aStalledCallFailsAtTheReadTimeoutInsteadOfWaiting() {
        script = attempt -> new Reply(200, answer(true), 3_000);
        GeminiClient slowAnswerClient = client("test-key", 3, Duration.ofMillis(300), 0);

        long start = System.nanoTime();
        assertThatThrownBy(() -> slowAnswerClient.generateJson("cv", SCHEMA)).isInstanceOf(AiServiceException.class);
        long elapsedMillis = (System.nanoTime() - start) / NANOS_PER_MILLISECOND;

        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(requests.get()).as("a timeout is not retried").isEqualTo(1);
    }

    @Test
    void failsOnAnAnswerWithoutContent() {
        script = attempt -> Reply.ok("{\"candidates\": []}");

        assertThatThrownBy(() -> client().generateJson("cv", SCHEMA)).isInstanceOf(AiServiceException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void failsWithoutCallingGoogleWhenNoApiKeyIsConfigured() {
        assertThatThrownBy(() -> client(" ", 3, Duration.ofSeconds(5), 0).generateJson("cv", SCHEMA))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("GEMINI_API_KEY");

        assertThat(requests.get()).isZero();
    }
}
