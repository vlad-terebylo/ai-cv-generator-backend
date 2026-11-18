# Sequence: when things go wrong

There are two kinds of failure. Some are known on the request thread and answered at once with an HTTP error. The others happen later on a worker, so the upload was already accepted and the failure is recorded on the job for the client to find by polling.

```mermaid
sequenceDiagram
    autonumber
    actor C as Client
    participant CC as CvController
    participant CIS as CvImportService
    participant JS as JobService
    participant JR as JobRepository
    participant W as Worker thread
    participant CS as CvService
    participant G as GeminiClient

    Note over C,JR: A. Known immediately: HTTP error, no job exists
    C->>CC: POST /api/v1/cvs
    alt file over 2 MB
        CC-->>C: 413 Payload Too Large
    else empty file, or not .pdf or .docx
        CC->>CIS: submit(document)
        CIS-->>C: 400 Bad Request
    else the worker queue is full
        CC->>CIS: submit(document)
        CIS->>JS: submit(label, task)
        JS->>JR: insert job
        JS->>JR: delete job again
        Note right of JS: the client never receives this id, so no record may remain
        JS-->>C: 503 Service Unavailable
    end

    Note over W,G: B. Found later on a worker: the job ends FAILED
    JS-->>W: queued task starts
    W->>JR: status PROCESSING
    W->>CS: process(document)
    alt text cannot be read, or the file is corrupt
        CS-->>W: UnreadableDocumentException
        W->>JR: FAILED, UNREADABLE_DOCUMENT
    else Gemini says it is not a CV, or name or email is missing
        CS-->>W: NotACvException
        W->>JR: FAILED, NOT_A_CV
    else Gemini failed, timed out, or answered with bad JSON
        CS->>G: generateJson
        G-->>G: retry 503 and 429 only, at most 3 attempts
        G-->>W: AiServiceException
        W->>JR: FAILED, AI_SERVICE_ERROR with a fixed safe message
    else any other exception
        CS-->>W: RuntimeException
        W->>JR: FAILED, INTERNAL_ERROR
    else a JVM Error
        CS-->>W: Error
        W->>JR: FAILED, INTERNAL_ERROR
        W-->>W: rethrow the Error
    end

    Note over C,JR: C. The client finds out by polling
    C->>CC: GET /api/v1/jobs/ID
    CC-->>C: status FAILED, errorCode, errorMessage
    C->>CC: GET /api/v1/jobs/ID after a restart
    CC-->>C: 404, jobs are only in memory
```

## How the mapping works

- A failure becomes an error code through the `JobFailure` interface. Exceptions that implement it supply their own `errorCode` and a message that is safe to show. `JobService` has no list of exception types.
- Anything that does not implement it becomes `INTERNAL_ERROR` with the generic message "The job could not be processed", and the details only go to the log.
- Retries apply only to Google's temporary errors (503 and 429). A timeout or any other error fails the job at once, so a stalled call cannot hold a worker thread.
- A malformed id in a path (`/api/v1/jobs/abc`) is a `400` from the mapper before any lookup happens.
