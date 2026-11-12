package org.example.aicvgenerator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/** Runs the real client against a tiny local HTTP server that plays Google. */
class GeminiClientTest {

    private record Reply(int status, String body, long delayMillis) {
        static Reply ok(String body) {
            return new Reply(200, body, 0);
        }

        static Reply status(int status) {
            return new Reply(status, "{\"error\": {\"status\": \"" + status + "\"}}", 0);
        }
    }

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
                "http://127.0.0.1:" + server.getAddress().getPort(), Duration.ofSeconds(2), readTimeout, maxAttempts,
                Duration.ofMillis(10), thinkingBudget);
        return new GeminiClient(properties, RestClient.builder(), json);
    }

    private GeminiClient client() {
        return client("test-key", 3, Duration.ofSeconds(5), 0);
    }

    @Test
    void parsesTheStructuredAnswerAndSendsTheKeyModelAndSchema() {
        CvExtraction extraction = client().extract("Valieria Karlova, Java developer");

        assertThat(extraction.isCv()).isTrue();
        assertThat(extraction.firstName()).isEqualTo("Valieria");
        assertThat(extraction.lastName()).isEqualTo("Karlova");
        assertThat(extraction.email()).isEqualTo("v.karlova@gmail.com");
        assertThat(extraction.experience()).singleElement().satisfies(job -> {
            assertThat(job.startYear()).isEqualTo(2025);
            assertThat(job.endYear()).isNull();
            assertThat(job.position()).isEqualTo("Senior Java Developer");
        });
        assertThat(paths).containsExactly("/models/gemini-test:generateContent");
        assertThat(apiKeys).containsExactly("test-key");
        assertThat(bodies.get(0)).contains("responseSchema").contains("Valieria Karlova, Java developer");
    }

    @Test
    void understandsAnAnswerSayingTheDocumentIsNotACv() {
        script = attempt -> Reply.ok(answer(false));

        assertThat(client().extract("Invoice 42").isCv()).isFalse();
    }

    @Test
    void asksTheModelNotToThinkWhenTheBudgetIsZero() {
        client("test-key", 3, Duration.ofSeconds(5), 0).extract("cv");

        assertThat(bodies.get(0)).contains("\"thinkingConfig\":{\"thinkingBudget\":0}");
    }

    @Test
    void leavesThinkingAloneWhenNoBudgetIsConfigured() {
        client("test-key", 3, Duration.ofSeconds(5), null).extract("cv");

        assertThat(bodies.get(0)).doesNotContain("thinkingConfig");
    }

    @Test
    void retriesWhenGoogleIsTemporarilyUnavailable() {
        script = attempt -> attempt < 3 ? Reply.status(503) : Reply.ok(answer(true));

        CvExtraction extraction = client().extract("cv");

        assertThat(extraction.isCv()).isTrue();
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void retriesWhenGoogleSaysTooManyRequests() {
        script = attempt -> attempt < 2 ? Reply.status(429) : Reply.ok(answer(true));

        assertThat(client().extract("cv").isCv()).isTrue();
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void givesUpAfterTheConfiguredNumberOfAttempts() {
        script = attempt -> Reply.status(503);

        assertThatThrownBy(() -> client().extract("cv")).isInstanceOf(AiServiceException.class)
                .hasMessageContaining("503");

        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryWhenRetriesAreDisabled() {
        script = attempt -> Reply.status(503);

        assertThatThrownBy(() -> client("test-key", 1, Duration.ofSeconds(5), 0).extract("cv"))
                .isInstanceOf(AiServiceException.class);

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void doesNotRetryErrorsThatRetryingCannotFix() {
        script = attempt -> Reply.status(400);

        assertThatThrownBy(() -> client().extract("cv")).isInstanceOf(AiServiceException.class);

        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void aStalledCallFailsAtTheReadTimeoutInsteadOfWaiting() {
        script = attempt -> new Reply(200, answer(true), 3_000);
        GeminiClient slowAnswerClient = client("test-key", 3, Duration.ofMillis(300), 0);

        long start = System.nanoTime();
        assertThatThrownBy(() -> slowAnswerClient.extract("cv")).isInstanceOf(AiServiceException.class);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMillis).isLessThan(2_000);
        assertThat(requests.get()).as("a timeout is not retried").isEqualTo(1);
    }

    @Test
    void failsOnAnAnswerWithoutContent() {
        script = attempt -> Reply.ok("{\"candidates\": []}");

        assertThatThrownBy(() -> client().extract("cv")).isInstanceOf(AiServiceException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void failsOnAnAnswerThatIsNotValidJson() {
        script = attempt -> Reply.ok(envelope("this is not json"));

        assertThatThrownBy(() -> client().extract("cv")).isInstanceOf(AiServiceException.class)
                .hasMessageContaining("malformed");
    }

    @Test
    void failsWithoutCallingGoogleWhenNoApiKeyIsConfigured() {
        assertThatThrownBy(() -> client(" ", 3, Duration.ofSeconds(5), 0).extract("cv"))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("GEMINI_API_KEY");

        assertThat(requests.get()).isZero();
    }
}
