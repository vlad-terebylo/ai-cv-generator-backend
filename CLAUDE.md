# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

CV import is implemented on `feature/cv-import` and is asynchronous (see `specs/spec-001-async-cv-import.md`):

- `POST /api/v1/cvs` takes a `.pdf`/`.docx` upload (max 2 MB), checks the type, creates an import job and returns `202 Accepted` with a `Location` header. It does not wait for the AI.
- A bounded worker pool then extracts the text (PDFBox / POI), asks Gemini whether it is a CV and to parse it, and upserts it by email into the in-memory repositories.
- `GET /api/v1/cv-imports/{id}` returns the job as a generic `JobResponse` (`id`, `status`, `entityType`, `entityId`, `errorCode`, `errorMessage`, `createdAt`, `updatedAt`). `status` is the `JobStatus` enum: `PENDING`, `PROCESSING`, `COMPLETED` (`entityType` is `CV` and `entityId` is the CV's id; both are null before that) or `FAILED` (`errorCode` is `NOT_A_CV`, `UNREADABLE_DOCUMENT`, `AI_SERVICE_ERROR` or `INTERNAL_ERROR`, plus a safe `errorMessage`). Clients poll it. The response only references the produced entity, it never embeds it. `JobResponse` is not CV-specific: another job type adds a constant to the `EntityType` enum and its own mapper, and only `CvImportDtoMapper` knows about CVs.
- `GET /api/v1/cvs/{id}` returns a stored CV.
- A non-CV, or a CV missing first name/last name/email, fails the job and saves nothing.
- Immediate errors: unsupported or empty file `400`, file over the limit `413`, worker queue full `503`, unknown job or CV `404`.

**Architecture rules live in `ARCHITECTURE.md` and are binding** (layer-first packages under `org.example.aicvgenerator`, in-memory storage only, MapStruct at each layer boundary, slow work off the request thread). Outbound AI calls go through the `client` package.

## Stack

- Gradle (use the wrapper), Java 17 toolchain, Spring Boot 4.1.1
- Spring MVC, MapStruct, Lombok, springdoc (Swagger UI at `/swagger-ui.html`)
- Storage is in-memory `ArrayList` repositories only; no database. CVs and import jobs are lost on restart, and polling an old job id then returns `404`.
- Config is `application.yaml`:
  - Upload limit: `spring.servlet.multipart.max-file-size` (2MB) and `max-request-size` (3MB).
  - Worker pool: `cv.import.pool-size` (4) and `cv.import.queue-capacity` (20).
  - Gemini needs `GEMINI_API_KEY`; the model is `GEMINI_MODEL`, default `gemini-3.8-flash` (as set in `application.yaml`). Without a key the job ends `FAILED` with `AI_SERVICE_ERROR`.
- Known limitation: Gemini calls have no timeout, so a stalled call keeps a worker thread busy until the app restarts.

## Commands

On Windows use `gradlew.bat` (or `./gradlew` in Git Bash).

- Build: `./gradlew build`
- Run app: `./gradlew bootRun` (base URL `http://localhost:8080`)
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests org.example.aicvgenerator.AiCvGeneratorApplicationTests`
- Single method: `./gradlew test --tests "org.example.aicvgenerator.AiCvGeneratorApplicationTests.methodName"`

## Testing the API by hand (Postman)

1. `POST /api/v1/cvs`, body `form-data`, key `file` of type File. Expect `202` and a job `id`.
2. Poll `GET /api/v1/cv-imports/{id}` until `status` is `COMPLETED` or `FAILED`.
3. On `COMPLETED`, `GET /api/v1/cvs/{entityId}`.
