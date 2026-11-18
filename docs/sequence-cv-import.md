# Sequence: uploading a CV

The normal path, from the upload to reading the stored CV. The request thread only validates and queues; a worker thread does the slow work.

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
    participant TX as DocumentTextExtractor
    participant AN as CvAnalyzer
    participant G as GeminiClient
    participant GO as Google Gemini
    participant CR as Cv and Experience repositories

    rect rgb(235, 245, 255)
    Note over C,JR: Request thread: fast
    C->>CC: POST /api/v1/cvs with the file
    CC->>CIS: submit(document)
    CIS->>TX: requireSupported(document)
    Note right of TX: not empty, .pdf or .docx
    CIS->>JS: submit(label, task)
    JS->>JR: insert job with status PENDING
    JS-->>W: queue the task
    JS-->>CIS: the new job
    CIS-->>CC: the new job
    CC-->>C: 202 Accepted, Location /api/v1/jobs/ID
    end

    rect rgb(240, 255, 240)
    Note over W,CR: Worker thread: slow, runs in parallel with other jobs
    W->>JR: status PROCESSING
    W->>CIS: run the task
    CIS->>CS: process(document)
    CS->>TX: extract(document)
    TX-->>CS: plain text
    CS->>AN: analyze(text)
    AN->>G: generateJson(prompt plus text, schema)
    G->>GO: generateContent
    GO-->>G: JSON answer
    G-->>AN: JSON text
    AN-->>CS: CvExtraction
    Note over CS: must be a CV with first name, last name and email, email is lower-cased
    CS->>CR: upsertByEmail, then replaceByCvId
    CR-->>CS: CV id, created or replaced
    CS-->>CIS: saved CV
    CIS-->>W: EntityRef CV and its id
    W->>JR: status COMPLETED, entityType CV, entityId
    end

    rect rgb(255, 250, 235)
    Note over C,CR: Client side: polling, then reading the result
    loop until COMPLETED or FAILED
        C->>CC: GET /api/v1/jobs/ID
        CC-->>C: status, and entityId once COMPLETED
    end
    C->>CC: GET /api/v1/cvs/ENTITY_ID
    CC->>CS: getById
    CS->>CR: find the CV and its experience
    CC-->>C: the CV
    end
```

## Things worth knowing

- `JobService` never learns what the task does. `CvImportService` passes it a function and gets back a reference (`EntityRef`) to what was produced.
- The CV upsert and the experience replacement are each atomic inside the repositories, so two workers importing the same email at once cannot both insert.
- Several jobs run side by side, one per worker thread. The number of threads and the queue size come from `cv.import.pool-size` and `cv.import.queue-capacity`.
