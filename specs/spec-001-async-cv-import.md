# spec-001: Asynchronous CV import

Status: implemented (phases 1 to 6), committed locally on `feature/cv-import`, not pushed
Branch: `feature/cv-import`
Revision: rewritten 2026-10-31 for the in-memory-only repository (commits `f76c5bf` "Created in memory repository realization" and `6c51020` "Deleted all JDBC and database mentions").
Binding rules: `ARCHITECTURE.md` (layer-first packages under `org.example.aicvgenerator`, in-memory storage only behind repository interfaces, MapStruct at each layer boundary). Do not add a database, JDBC, JPA, Flyway or migrations: section 7 of `ARCHITECTURE.md` forbids it without an amendment.

## 1. Current state (what this spec builds on)

- Storage is `ArrayList`-backed `InMemoryCvRepository` and `InMemoryExperienceRepository` behind the `CvRepository` and `ExperienceRepository` interfaces. Their methods are `synchronized`, they store and return copies, assign ids and timestamps, and `InMemoryCvRepository` throws `DuplicateEmailException` on a duplicate email. All data is lost on restart.
- There is no datasource, no transaction manager and no `@Transactional` anywhere, so nothing holds a database connection.
- `CvService.process(CvDocument)` is synchronous: extract text, call Gemini, validate, upsert by email. `POST /api/v1/cvs` calls it on the request thread, so Postman waits for the whole Gemini call.
- `application.yaml` has `max-file-size: 5MB` / `max-request-size: 5MB`, and `GlobalExceptionHandler.handleTooLarge` hardcodes "5 MB".
- Tests: `CvServiceTest` (Mockito), `CvControllerTest` (`@WebMvcTest`), `RepositoryContractTest` + `InMemoryRepositoryTest`, `AiCvGeneratorApplicationTests` (context loads with in-memory repositories).

## 2. Problem

The upload request thread is blocked for the full duration of the Gemini call. Slow or hung AI calls make Postman (or any client) wait with no feedback.

## 3. Goals

1. Make the upload non-blocking: `POST` returns `202 Accepted` immediately and the work runs on a bounded thread pool.
2. Let clients track progress through an import job stored in an in-memory repository.
3. Lower the maximum upload size from 5 MB to 2 MB.

## 4. Non-goals and dropped items

- **Dropped: "keep Gemini out of the transaction".** There are no transactions or database connections any more, so the earlier `CvService` analyze/save split is not needed. The worker simply calls the existing `CvService.process`.
- **Dropped: database work.** No migration, JDBC repository, `jdbc` profile, Testcontainers or restart cleanup. After a restart all jobs and CVs are gone, and polling an old job id returns `404`.
- No Gemini connect/read timeouts in the original scope (skipped by the user at the time); added later, see "Follow-up: Gemini resilience".
- No retries, no job expiry/cleanup, no cancellation.
- No storage of the uploaded file beyond the in-memory handoff to the worker.
- No change to `GET /api/v1/cvs/{id}`.

## 5. Decisions (already agreed)

| Topic | Decision |
|---|---|
| Job tracking | New import-job entity with an in-memory repository (interface + `InMemory*` implementation, like the existing ones) |
| Old sync endpoint | Replaced: `POST /api/v1/cvs` now returns `202` |
| Executor | Bounded `ThreadPoolTaskExecutor` (4 threads, queue of 20); full queue returns `503` |
| Gemini timeouts | Skipped |
| Upload limit | 2 MB file, 3 MB request (multipart overhead) |

## 6. Target design

```
POST /api/v1/cvs (file)         -> 202 Accepted, Location: /api/v1/cv-imports/{jobId}, body {id, status: PENDING, ...}
   request thread: check extension + non-empty, create job (PENDING), queue work
   worker thread:  job -> PROCESSING
                   CvService.process(document)   (extract text, call Gemini, validate, upsert by email)
                   job -> COMPLETED (cvId)   |   FAILED (errorCode, errorMessage)

GET /api/v1/cv-imports/{jobId}  -> 200 job | 404
GET /api/v1/cvs/{cvId}          -> stored CV (unchanged)
```

Fast local failures still fail synchronously: unsupported extension / empty file -> `400`, file too large -> `413`, queue full -> `503`. Everything that depends on the document content or on Gemini is reported on the job as `FAILED`.

### 6.1 Job model

`CvImport` (domain, `service.model`), `CvImportEntity` (row, `repository.entity`), `CvImportStatus` enum (`service.model`).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | generated in the service |
| `status` | `CvImportStatus` | `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED` (stored as a String in the entity) |
| `fileName` | String | original upload name |
| `cvId` | Long, nullable | set when `COMPLETED` |
| `errorCode` | String, nullable | `NOT_A_CV`, `UNREADABLE_DOCUMENT`, `AI_SERVICE_ERROR`, `INTERNAL_ERROR` |
| `errorMessage` | String, nullable | user-safe text, never a stack trace |
| `createdAt`, `updatedAt` | Instant | assigned by the repository |

### 6.2 API

| Endpoint | Success | Errors |
|---|---|---|
| `POST /api/v1/cvs` (multipart `file`) | `202` + `Location` + `JobResponse` | `400` unsupported/empty file, `413` too large, `503` queue full |
| `GET /api/v1/cv-imports/{id}` | `200` `JobResponse` | `404` unknown id |

`JobResponse` (renamed from `CvImportResponse` and made entity-neutral after Phase 6): `id`, `status` (`JobStatus` enum), `entityType` (`EntityType` enum, currently only `CV`), `entityId`, `errorCode`, `errorMessage`, `createdAt`, `updatedAt`. It only references the produced entity and never embeds it: `entityType` and `entityId` are `null` until the job is `COMPLETED`, then `CV` and the CV's id. The uploaded file name is no longer exposed. The CV-specific `cvId` and `fileName` stay on the domain model and entity, and only `CvImportDtoMapper` maps `cvId` to `entityType` / `entityId`. A future job type adds an `EntityType` constant and reuses `JobResponse`.

---

## Phase 1: Upload size limit

**Goal:** enforce 2 MB and keep the error message in sync with the configuration.

**Tasks**
1. In `src/main/resources/application.yaml` set `spring.servlet.multipart.max-file-size: 2MB` and `max-request-size: 3MB`.
2. In `GlobalExceptionHandler.handleTooLarge`, stop hardcoding "5 MB": inject `spring.servlet.multipart.max-file-size` and build the message from it.

**Files:** `application.yaml`, `exception/GlobalExceptionHandler.java`.

**Tests**
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` with a real HTTP call (`RestClient` against `@LocalServerPort`; `RestClient` is already on the classpath through `spring-boot-starter-restclient`): a file just over 2 MB posted to `/api/v1/cvs` -> `413` with the configured limit in the message. `MockMvc` bypasses the real multipart limits, so it cannot test this.
- A file just under 2 MB is not rejected for size (with the sync endpoint still in place it reaches Gemini and fails with `502` when no key is set).

**Done when:** over-limit uploads return `413` mentioning 2 MB; `./gradlew test` passes.

---

## Phase 2: Import-job storage (in-memory)

**Goal:** a job entity and repository that follow the same pattern as the CV repositories.

**Tasks**
1. `repository/entity/CvImportEntity` (Lombok, `@Builder(toBuilder = true)`, row-shaped; status stored as a String).
2. `repository/CvImportRepository` interface: `void insert(CvImportEntity)` (the id is generated by the service), `Optional<CvImportEntity> findById(UUID)`, `void update(CvImportEntity)`, `void delete(UUID)` (added after Phase 5, see Phase 3 task 3).
3. `repository/InMemoryCvImportRepository` (`@Repository`, `ArrayList`, `synchronized`, stores and returns copies, sets `createdAt`/`updatedAt` on insert and bumps `updatedAt` on update, `update` of an unknown id is a no-op like `InMemoryCvRepository`).
4. Domain `service/model/CvImport` and `CvImportStatus`; MapStruct `mapper/CvImportEntityMapper` (domain <-> entity, status enum <-> string).

**Files:** `CvImportEntity`, `CvImportRepository`, `InMemoryCvImportRepository`, `CvImport`, `CvImportStatus`, `CvImportEntityMapper`.

**Tests**
- `CvImportRepositoryContractTest` (abstract, same pattern as `RepositoryContractTest`) with an `InMemoryCvImportRepositoryTest` subclass: insert + find by id, update changes status/cvId/error fields and bumps `updatedAt`, unknown id -> empty, delete removes only that job and ignores unknown ids, returned entities are copies.
- Mapper unit test for the enum/string conversion.

**Done when:** the contract test passes; no database, Flyway or JDBC types are introduced.

---

## Phase 3: Executor, import service, processor

**Goal:** accept a document, run it in the background, record the outcome.

**Tasks**
1. `config/CvImportProperties` (`@ConfigurationProperties("cv.import")`: `poolSize`, `queueCapacity`) and `application.yaml` entries (`cv.import.pool-size: 4`, `cv.import.queue-capacity: 20`). `@ConfigurationPropertiesScan` is already on the application class.
2. `config/AsyncConfig`: `cvImportExecutor` bean (`ThreadPoolTaskExecutor`, thread-name prefix `cv-import-`, `AbortPolicy` so a full queue throws `TaskRejectedException`, wait for running tasks on shutdown).
3. `service/CvImportService`:
   - `submit(CvDocument)`: reject unsupported extension / empty content up front (`UnsupportedDocumentException`, `UnreadableDocumentException`, no job created), create job `PENDING`, call `executor.execute(() -> processor.run(jobId, document))`. On `TaskRejectedException`, delete the just-created job and throw `ServerBusyException`. (Originally the job was marked `FAILED/SERVER_BUSY`, but the `503` carries no job id, so that record was unreachable and only wasted memory; changed after Phase 5.)
   - `getById(UUID)`: throws `CvImportNotFoundException` if missing.
4. `service/CvImportProcessor.run(UUID, CvDocument)`: job -> `PROCESSING`; `cvService.process(document)`; job -> `COMPLETED` with the saved CV's id. Catch `NotACvException` -> `NOT_A_CV`, `UnreadableDocumentException` / `UnsupportedDocumentException` -> `UNREADABLE_DOCUMENT`, `AiServiceException` -> `AI_SERVICE_ERROR`, any other `RuntimeException` -> `INTERNAL_ERROR` with a generic message (log the stack trace server-side). The processor never rethrows, so a worker thread cannot die on a job failure.
5. New exceptions in `exception`: `ServerBusyException`, `CvImportNotFoundException`.
6. `DocumentTextExtractor.requireSupported(CvDocument)`: the cheap "non-empty and `.pdf`/`.docx`" check, used by both `extract` and `CvImportService.submit` so the rule is not duplicated.
7. Concurrency fix (added during implementation, approved by the user): import workers run in parallel, so two workers saving the same email could both see "no CV yet" and the second insert would fail with `DuplicateEmailException`. `CvService.save` is now `synchronized` so the email lookup + insert is atomic. Only the quick save is serialized, not the Gemini call.

**Files:** `CvImportProperties`, `AsyncConfig`, `CvImportService`, `CvImportProcessor`, two exceptions, `DocumentTextExtractor`, `CvService`, `application.yaml`.

**Layering:** `CvImportService` -> `CvImportProcessor` -> `CvService` -> repositories / `client`; `CvImportService` and the processor also use `CvImportRepository` and `CvImportEntityMapper`. No cycles, and nothing depends on controller types. The executor is injected as a `TaskExecutor` qualified by `cvImportExecutor`.

**Tests (Mockito, no Spring)**
- `CvImportServiceTest`: job created `PENDING` and task queued; unsupported extension and empty file rejected without creating a job; rejected execution -> `ServerBusyException`, the job is deleted and the processor is never called; `getById` unknown -> `CvImportNotFoundException`.
- `CvImportProcessorTest`: success -> `PROCESSING` then `COMPLETED` with `cvId`; `NotACvException`, `UnreadableDocumentException`, `AiServiceException` and an unexpected exception each give `FAILED` with the matching code; the processor does not rethrow.
- `CvServiceConcurrencyTest`: two threads import the same email against the real in-memory repositories (with a deliberately slow email lookup to widen the race); both succeed and exactly one creates the CV. Verified to fail without the `synchronized` fix.
- `DocumentTextExtractorTest`: `requireSupported` accepts `.pdf`/`.docx` in any case and rejects other extensions, missing names and empty content.
- Existing context test: the `cvImportExecutor` bean exists with the configured pool size, queue capacity and thread-name prefix.

**Done when:** all of the above pass without a Spring context.

---

## Phase 4: Web layer

**Goal:** expose the async flow over HTTP.

**Tasks**
1. `controller/dto/JobResponse` (record, springdoc `@Schema` annotations; originally `CvImportResponse`).
2. `mapper/CvImportDtoMapper` (domain -> response).
3. `CvController.upload` now calls `CvImportService.submit` and returns `202` with `Location: /api/v1/cv-imports/{id}` and the `JobResponse`. Update its springdoc annotations (`202`, `400`, `413`, `503`).
4. New `CvImportController`: `GET /api/v1/cv-imports/{id}` -> `200` or `404` (one controller per resource).
5. `GlobalExceptionHandler`: `ServerBusyException` -> `503`, `CvImportNotFoundException` -> `404`. The `NotACvException` and `AiServiceException` handlers stay only if something else can still throw them to the web layer; otherwise remove them.

**Files:** `JobResponse`, `CvImportDtoMapper`, `CvController`, `CvImportController`, `GlobalExceptionHandler`.

**Tests (`@WebMvcTest`, services mocked)**
- `POST` -> `202`, `Location` header, body with `id` and `PENDING`.
- `POST` with unsupported file -> `400`; service throws busy -> `503`; missing `file` part -> `400`.
- `GET /cv-imports/{id}` -> `200` for `PENDING`, `COMPLETED` (with `entityType` = `CV` and `entityId`) and `FAILED` (with error fields); unknown id -> `404`.
- Update the existing `CvControllerTest` upload cases (they currently expect `201`/`200`/`422` from the sync flow).

**Done when:** controller tests pass; Swagger UI shows the new contract.

---

## Phase 5: End-to-end verification

**Goal:** prove the request thread is no longer blocked and the whole flow works.

**Tests** (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@MockitoBean GeminiClient`, real HTTP via `RestClient`; Awaitility ships with `spring-boot-starter-test`)
1. **Non-blocking:** the mocked `GeminiClient.extract` waits on a `CountDownLatch`. `POST` must return `202` while the latch is still closed; then release the latch, poll `GET /cv-imports/{id}` until `COMPLETED` (with a timeout), and fetch the CV with `GET /cvs/{cvId}`.
2. **Failure path:** the mock returns "not a CV" -> the job ends `FAILED` / `NOT_A_CV`, and no CV is stored.
3. **Upsert path:** two uploads with the same email -> the second job is `COMPLETED` with the same `cvId`.
4. **Queue full:** with a tiny executor (pool 1, queue 1 via test properties) and a blocked mock, a later `POST` returns `503` (its job is deleted, which `CvImportServiceTest` verifies because the response carries no job id) while the accepted jobs still complete.

Fixtures: a tiny real `.pdf` and `.docx` generated in the test with PDFBox / POI, so text extraction really runs.

**Done when:** `./gradlew test` is green, including the new end-to-end tests.

---

## Phase 6: Documentation

**Tasks**
1. `ARCHITECTURE.md`:
   - add a rule that long-running work (the Gemini call) never runs on the request thread: it runs through the `cvImportExecutor` and its progress is recorded in an import job;
   - mention the executor/properties in the `config` row and `CvImportRepository` in the `repository` row;
   - extend section 9 (testing) with the job-repository contract test and the end-to-end test;
   - keep section 7: storage stays in-memory only.
2. `CLAUDE.md`: new endpoints (`POST` -> `202`, `GET /api/v1/cv-imports/{id}`), the 2 MB limit, the executor settings, and that jobs are lost on restart. While there, fix the existing mismatch where `CLAUDE.md` names `gemini-2.5-flash` as the default model but `application.yaml` sets `gemini-3.8-flash`.
3. In the final summary, tell the user how to test in Postman: submit, then poll the job until `COMPLETED` / `FAILED`.

**Done when:** the docs match the code; no mention of the sync response or the 5 MB limit remains.

---

## Follow-up: Gemini resilience (added after Phase 6)

Real use showed jobs that take very long. Cause: the Gemini call had no timeout, so a stalled or very slow call kept a worker thread busy and the job in `PROCESSING`. Changes:

1. `GeminiClient` has connect (5s) and read (60s) timeouts, set from `cv.gemini.*`; a timeout fails the job with `AI_SERVICE_ERROR`.
2. It retries only Google's temporary errors (503, 429), at most `max-attempts` (3) times with a growing pause; timeouts and other errors are not retried.
3. The request can set `thinkingConfig.thinkingBudget` (`cv.gemini.thinking-budget`). It was first defaulted to 0, but a model that does not take a budget answered `400 INVALID_ARGUMENT`, so it is now empty by default (nothing sent, works with every model) and opt-in with `GEMINI_THINKING_BUDGET=0` for models such as Gemini 2.5.
4. `CvImportProcessor` also catches a JVM `Error`, records it on the job as `INTERNAL_ERROR` and rethrows it, so a job cannot stay in `PROCESSING`.
5. Timing is logged per job (text extraction, Gemini wait, saving, total).

Tests: `GeminiClientTest` runs the real client against a local HTTP server (parsing, retries on 503/429, no retry on 400 or timeout, read timeout fails fast, empty/malformed answers, missing key); `CvImportProcessorTest` covers the `Error` case. Verified that removing the read timeout makes the timeout test fail.

## 7. Risks

- **~~No Gemini timeouts~~ (fixed in the follow-up above):** originally a stalled Gemini call kept its worker thread busy indefinitely. If all 4 threads stall, new jobs stay `PENDING`; after 20 queued jobs, `POST` returns `503`. The upload endpoint itself stays fast, but processing stops until restart. Adding connect/read timeouts later is a small change to the `GeminiClient` / `RestClient` setup.
- **Breaking API change:** every caller of `POST /api/v1/cvs` must switch to polling.
- **Lost data on restart:** jobs, CVs and queued uploads exist only in memory. After a restart `GET /api/v1/cv-imports/{id}` returns `404` for old ids.
- **Unbounded memory growth:** finished jobs are never removed, and with in-memory storage they accumulate on the heap. Job expiry or a size cap is out of scope here but worth a follow-up.
- **No atomic saves:** a failure between writing the CV row and its experience rows is not rolled back (already true today); the job would end `FAILED/INTERNAL_ERROR` with a partially saved CV.

## 8. Order and verification

Phases run in order; each leaves `./gradlew test` green before the next starts. Commits only when the user asks.
