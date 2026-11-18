# ARCHITECTURE.md

Binding architecture rules for this project. All new code MUST comply. If a task requires breaking a rule, stop and ask the user to amend this document first.

## 1. Style

Layered architecture: **controller → service → repository**. REST JSON API under `/api/v1`. Persistence is in-memory `ArrayList` storage behind repository interfaces; no database is used. Slow work (the Gemini call) runs asynchronously on a bounded worker pool, never on a request thread (see section 7). Services are concrete classes (no interface/`Impl` pair unless a second implementation actually exists; repositories are the exception).

## 2. Packages (layer-first)

Base package: `org.example.aicvgenerator`

| Package | Contents |
|---|---|
| `controller` | `@RestController` classes |
| `controller.dto` | Request/response DTOs (`*Request`, `*Response`) |
| `service` | `@Service` classes |
| `service.model` | Domain models |
| `repository` | Repository interfaces (`CvRepository`, `ExperienceRepository`, `JobRepository`) and `InMemory*Repository` (`ArrayList`) implementations |
| `repository.entity` | Entities (rows as persisted, `*Entity`) |
| `mapper` | MapStruct mappers |
| `config` | `@Configuration` classes, `@ConfigurationProperties`, and the `cvImportExecutor` thread pool (`AsyncConfig`, `CvImportProperties`) |
| `client` | Outbound clients to external services (e.g. Gemini), used by services |
| `exception` | Domain exceptions and the global `@RestControllerAdvice` |

No other top-level packages without amending this document. No feature sub-packages.

## 3. Dependency rules

Allowed direction only:

```
controller ──► service ──► repository
     └───────────┴───────────┴──► mapper (used by the layer doing the conversion)
```

- Controller MUST NOT depend on repository or entities.
- Service MUST NOT depend on controller or on `controller.dto`.
- Repository MUST NOT depend on service, controller, domain models, or DTOs.
- Layers depend only on the layer directly below. No layer skipping, no upward or circular dependencies.
- Controllers do not call other controllers; services may call other services but never create cycles; repositories do not call other repositories' services.
- `client` is called only by services; it depends on nothing but `config`, and it returns/accepts its own small types, never DTOs, entities or domain models.
- `client` is transport only: no prompts, no domain schemas, no parsing into domain types. `GeminiClient.generateJson(prompt, schema)` returns the answer text; `CvAnalyzer` (service) owns the CV prompt, schema, length limit and parsing, with the CV constants in `CvAnalysisConstants`.
- Every outbound call in `client` has explicit connect and read timeouts (configured in `config`), so a stalled remote service can never hold a worker thread. Only a remote service's temporary errors (503, 429) are retried, with a bounded number of attempts; timeouts and other errors fail immediately.
- `config`, `exception`, and `mapper` may be used by any layer but must not contain business logic.

## 4. Models per layer

| Layer | Model | Naming |
|---|---|---|
| Controller | Request/response DTO | `CreateCvRequest`, `CvResponse` |
| Service | Domain model | `Cv` |
| Repository | Entity | `CvEntity` |

- A model never crosses its layer's boundary. Controllers never return or accept domain models or entities; repositories never return DTOs.
- Conversion happens at each boundary via MapStruct mappers: controller maps DTO ↔ domain; service maps domain ↔ entity.
- Entities are plain row-shaped data objects (Lombok allowed); they carry no business logic.
- Domain models hold business state/rules and are independent of web and persistence concerns (no Jackson or validation annotations).

## 5. Layer responsibilities

**Controller**: HTTP only: routing, `@Valid` on request DTOs, status codes, DTO ↔ domain mapping. No business logic.

**Service**: business logic and orchestration. Throws domain exceptions from `exception`. Works with domain models only.

**Repository**: data access only. Services depend on the repository interface, never on an implementation. In-memory implementations store copies in `ArrayList`s and are `synchronized`. They take and return entities, assign ids and timestamps, enforce unique email (`DuplicateEmailException`) and ordering. No business rules.

**Mapper**: MapStruct interfaces with `componentModel = "spring"`. Pure conversion, no logic beyond field mapping, no repository/service injection.

**Config**: bean definitions and typed properties only.

## 6. Error handling and validation

- Input validation: Bean Validation annotations on request DTOs, triggered by `@Valid` in controllers.
- Business rule violations: domain exceptions thrown by services.
- A single global `@RestControllerAdvice` in `exception` translates exceptions to HTTP error responses. Controllers do not catch-and-translate.

## 7. Persistence and background work

- Storage is in-memory only; do not add a database, JDBC, JPA or migrations without amending this document. All CVs and jobs are lost on restart.
- Long-running work (the Gemini call) never runs on a request thread. The controller returns `202 Accepted` with a job (`Job`: `PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`); the work runs on the bounded `cvImportExecutor` (`cv.import.pool-size`, `cv.import.queue-capacity`) and its outcome is recorded on the job.
- If the queue is full, `JobService.submit` deletes the new job and throws `ServerBusyException` (`503`). The client never receives that job's id, so no record of it may remain.
- Code running on a worker thread must not let an exception escape: record the failure on the job (`errorCode`, user-safe `errorMessage`) and log the details. A JVM `Error` is recorded on the job the same way and then rethrown, so a job can never stay in `PROCESSING`. `JobService.run` is the only place that does this.
- Check-then-write sequences that parallel workers can race on are made atomic in the repository, not with `synchronized` in a service: `CvRepository.upsertByEmail` and `ExperienceRepository.replaceByCvId` each do their whole sequence under the repository lock. Services call these single methods.

## 8. API documentation

- OpenAPI via springdoc, generated from controllers.
- Document endpoints on controllers/DTOs with OpenAPI annotations; keep these annotations out of `service` and `repository`.

## 9. Testing rules

| Layer | Test type |
|---|---|
| Controller | `@WebMvcTest` with the service mocked |
| Service | Plain unit tests with mocked repositories (Mockito), no Spring context |
| Repository | Shared contract tests (`RepositoryContractTest`, `JobRepositoryContractTest`) run against the in-memory implementations as plain unit tests |
| Mapper | Unit tests for non-trivial mappings |
| End to end | `@SpringBootTest` on a random port in the `e2e` test package: real HTTP, real generated PDF/DOCX files, the real executor; only `GeminiClient` is mocked |

- Tests mirror the main package structure; end-to-end tests live in `e2e`.
- Tests must not bypass layering (e.g. a controller test must not reach a repository).
- Multipart size limits are enforced by the servlet container, so they are tested against a real server, not with `MockMvc`.
- Tests of asynchronous behaviour wait with Awaitility and use a client read timeout, so a regression fails fast instead of hanging.

## 10. Comments

- A comment is written only for what the code does not say: why it is done this way, invariants, external contracts, non-obvious links between modules, traps.
- Public boundaries (controller endpoints, service and repository interface methods, `client` classes, `@ConfigurationProperties`) have a Javadoc stating the contract: result, thrown exceptions, assumptions. Agents often find code by grep and do not read the body. Obvious getters and trivial private methods need none.
- Forbidden: comments that retell the code, commented-out code, and diary comments (who or when changed what, "previously", "now", "TODO later"). Git holds the history.
- A stale comment (including Javadoc, `application.yaml` comments, OpenAPI descriptions and the docs `CLAUDE.md` / `ARCHITECTURE.md`) is a bug and is fixed in the same change as the code it describes.

## 11. Dependencies required by this architecture

`spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-restclient` (Gemini client), PDFBox and Apache POI (text extraction), MapStruct (+ processor, ordered after Lombok with `lombok-mapstruct-binding`), springdoc-openapi. Add them only as the code that needs them is introduced.
