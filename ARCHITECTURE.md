# ARCHITECTURE.md

Binding architecture rules for this project. All new code MUST comply. If a task requires breaking a rule, stop and ask the user to amend this document first.

## 1. Style

Layered architecture: **controller → service → repository**. REST JSON API under `/api/v1`. Persistence is in-memory `ArrayList` storage behind repository interfaces; no database is used. Services are concrete classes (no interface/`Impl` pair unless a second implementation actually exists; repositories are the exception).

## 2. Packages (layer-first)

Base package: `org.example.aicvgenerator`

| Package | Contents |
|---|---|
| `controller` | `@RestController` classes |
| `controller.dto` | Request/response DTOs (`*Request`, `*Response`) |
| `service` | `@Service` classes |
| `service.model` | Domain models |
| `repository` | Repository interfaces (`CvRepository`) and `InMemory*Repository` (`ArrayList`) implementations |
| `repository.entity` | Entities (rows as persisted, `*Entity`) |
| `mapper` | MapStruct mappers |
| `config` | `@Configuration` classes and `@ConfigurationProperties` |
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
- `client` is called only by services; it depends on nothing but `config`, and it returns/accepts its own small types, never DTOs or entities.
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

## 7. Persistence

- Storage is in-memory only; do not add a database, JDBC, JPA or migrations without amending this document.

## 8. API documentation

- OpenAPI via springdoc, generated from controllers.
- Document endpoints on controllers/DTOs with OpenAPI annotations; keep these annotations out of `service` and `repository`.

## 9. Testing rules

| Layer | Test type |
|---|---|
| Controller | `@WebMvcTest` with the service mocked |
| Service | Plain unit tests with mocked repositories (Mockito), no Spring context |
| Repository | `RepositoryContractTest` (shared contract) run against the in-memory implementation as a plain unit test |
| Mapper | Unit tests for non-trivial mappings |

- Tests mirror the main package structure.
- Tests must not bypass layering (e.g. a controller test must not hit the database).

## 10. Dependencies required by this architecture

`spring-boot-starter-webmvc`, `spring-boot-starter-validation`, MapStruct (+ processor, ordered after Lombok with `lombok-mapstruct-binding`), springdoc-openapi. Add them only as the code that needs them is introduced.
