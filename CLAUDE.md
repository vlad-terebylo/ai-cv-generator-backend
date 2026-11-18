# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

CV import is implemented on `feature/cv-import` and is asynchronous (see `specs/spec-001-async-cv-import.md`):

- `POST /api/v1/cvs` takes a `.pdf`/`.docx` upload (max 2 MB), checks the type, creates a job and returns `202 Accepted` with a `Location` header. It does not wait for the AI.
- A bounded worker pool then extracts the text (PDFBox / POI), asks Gemini whether it is a CV and to parse it, and upserts it by email into the in-memory repositories.
- `GET /api/v1/jobs/{id}` returns the job as a generic `JobResponse` (`id`, `status`, `entityType`, `entityId`, `errorCode`, `errorMessage`, `createdAt`, `updatedAt`). `status` is the `JobStatus` enum: `PENDING`, `PROCESSING`, `COMPLETED` (`entityType` is `CV` and `entityId` is the CV's id; both are null before that) or `FAILED` (`errorCode` is `NOT_A_CV`, `UNREADABLE_DOCUMENT`, `AI_SERVICE_ERROR` or `INTERNAL_ERROR`, plus a safe `errorMessage`). Clients poll it. The response only references the produced entity, it never embeds it. `JobResponse` is not CV-specific: `JobService` runs any `JobTask` in the background and knows nothing about CVs (failures are classified through the `JobFailure` interface that exceptions implement). Only `CvImportService` knows that a CV import is a task: another job type adds a constant to the `EntityType` enums (service and DTO) and its own task.
- `GET /api/v1/cvs/{id}` returns a stored CV.
- Every id in a response DTO is an opaque `String` (job `id`, `entityId`, CV `id`); controllers take path ids as `String` and the DTO mappers convert them (`toCvId`, `toJobId`; a malformed id is `400`), while the service and repository layers keep `UUID` / `long`.
- A non-CV, or a CV missing first name/last name/email, fails the job and saves nothing.
- Immediate errors: unsupported or empty file `400`, file over the limit `413`, worker queue full `503`, unknown job or CV `404`.

**Diagrams of the service and of the main flows are in `docs/`** (start with `docs/README.md`); keep them in step with the code.

**Architecture rules live in `ARCHITECTURE.md` and are binding** (layer-first packages under `org.example.aicvgenerator`, in-memory storage only, MapStruct at each layer boundary, slow work off the request thread). Outbound AI calls go through the `client` package.

## Comments

Binding for all code, config and docs (full rules: `ARCHITECTURE.md` section 10).

- A comment says only what the code does not: why it is done this way, invariants, external contracts, links between modules, traps.
- Public boundaries (controllers, services, repository interfaces, `client`, config properties) carry a Javadoc with the contract (what it returns, what it throws, what it assumes). Agents find these with grep and do not read the body.
- Forbidden: comments that retell the code, commented-out code, and diary comments ("added", "changed", "fixed", "previously", "TODO later"). History lives in git.
- A stale comment is a bug: fix it in the same change as the code.

## Stack

- Gradle (use the wrapper), Java 17 toolchain, Spring Boot 4.1.1
- Spring MVC, MapStruct, Lombok, springdoc (Swagger UI at `/swagger-ui.html`)
- Storage is in-memory `ArrayList` repositories only; no database. CVs and jobs are lost on restart, and polling an old job id then returns `404`.
- Config is `application.yaml`:
  - Upload limit: `spring.servlet.multipart.max-file-size` (2MB) and `max-request-size` (3MB).
  - Worker pool: `cv.import.pool-size` (4) and `cv.import.queue-capacity` (20).
  - Gemini needs `GEMINI_API_KEY`; the model is `GEMINI_MODEL`, default `gemini-3.5-flash-lite` (as set in `application.yaml`). Without a key the job ends `FAILED` with `AI_SERVICE_ERROR`.
  - Gemini client (`cv.gemini.*`): `connect-timeout` 5s and `read-timeout` 60s, so a stalled call fails the job instead of holding a worker thread; `max-attempts` 3 with a growing `retry-delay` (1s, 2s) for Google's temporary errors only (503 and 429; other errors and timeouts are not retried); `thinking-budget` (`GEMINI_THINKING_BUDGET`) is empty by default, which sends nothing about thinking and works with every model. Set it to 0 only for models that take a thinking budget (e.g. Gemini 2.5) to switch thinking off for speed; Gemini 3 models are documented with thinking levels instead, and a model that dislikes the field answers `400 INVALID_ARGUMENT`.
- Timing is logged for every job: `Gemini … answered in N ms`, `Processed <file>: text extraction N ms, Gemini N ms, saving N ms` and `Job <id> took N ms`. Use them to see where the time goes.

## Commands

On Windows use `gradlew.bat` (or `./gradlew` in Git Bash).

- Build: `./gradlew build`
- Run app: `./gradlew bootRun` (base URL `http://localhost:8080`)
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests org.example.aicvgenerator.AiCvGeneratorApplicationTests`
- Single method: `./gradlew test --tests "org.example.aicvgenerator.AiCvGeneratorApplicationTests.methodName"`

## Testing the API by hand (Postman)

1. `POST /api/v1/cvs`, body `form-data`, key `file` of type File. Expect `202` and a job `id`.
2. Poll `GET /api/v1/jobs/{id}` until `status` is `COMPLETED` or `FAILED`.
3. On `COMPLETED`, `GET /api/v1/cvs/{entityId}`.
