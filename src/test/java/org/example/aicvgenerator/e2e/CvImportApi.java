package org.example.aicvgenerator.e2e;

import java.time.Duration;
import java.util.Set;

import org.awaitility.Awaitility;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/** Calls the running application over real HTTP; error statuses are returned, not thrown. */
final class CvImportApi {

    private static final Set<String> FINISHED = Set.of("COMPLETED", "FAILED");

    private final RestClient client;

    CvImportApi(int port) {
        // a request that waits for the (blocked) AI call fails fast instead of hanging the test
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl("http://localhost:" + port).requestFactory(requestFactory).build();
    }

    ResponseEntity<JsonNode> upload(String fileName, byte[] content) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        return client.post().uri("/api/v1/cvs").body(body).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(JsonNode.class);
    }

    ResponseEntity<JsonNode> importStatus(String jobId) {
        return get("/api/v1/cv-imports/" + jobId);
    }

    ResponseEntity<JsonNode> cv(long id) {
        return get("/api/v1/cvs/" + id);
    }

    /** Polls the job until it is COMPLETED or FAILED and returns that final state. */
    JsonNode awaitFinished(String jobId) {
        return Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50))
                .until(() -> importStatus(jobId).getBody(), job -> FINISHED.contains(job.get("status").asString()));
    }

    private ResponseEntity<JsonNode> get(String path) {
        return client.get().uri(path).retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(JsonNode.class);
    }
}
