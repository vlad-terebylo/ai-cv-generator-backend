# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

CV import feature is implemented on `feature/cv-import`: `POST /api/v1/cvs` takes a `.pdf`/`.docx` upload, extracts text (PDFBox / POI), asks Gemini whether it is a CV and to parse it, then upserts it by email into the repositories (in-memory by default, Postgres with the `jdbc` profile). `GET /api/v1/cvs/{id}` returns a stored CV. A non-CV, or a CV missing first name/last name/email, is rejected with 422 and nothing is saved.

**Architecture rules live in `ARCHITECTURE.md` and are binding** (layer-first packages under `org.example.aicvgenerator`, JDBC not JPA, MapStruct at each layer boundary, Flyway-only schema). Outbound AI calls go through the `client` package.

## Stack

- Gradle (use the wrapper), Java 17 toolchain, Spring Boot 4.1.1
- Spring MVC, `JdbcTemplate` on PostgreSQL, Flyway, MapStruct, Lombok, springdoc (Swagger UI at `/swagger-ui.html`)
- Persistence mode is chosen by Spring profile. Default: in-memory `ArrayList` repositories, no database needed (data is lost on restart). `--spring.profiles.active=jdbc`: PostgreSQL repositories plus Flyway; start the DB with `docker compose up -d db`. In memory mode the datasource/JDBC/Flyway auto-configuration is excluded in `application.yaml`.
- Config is `application.yaml`. DB defaults (jdbc profile) match `docker-compose.yaml` (override with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Gemini needs `GEMINI_API_KEY` (optional `GEMINI_MODEL`, default `gemini-2.5-flash`); without it uploads fail with 502.
- Tests: the JDBC repository test and the jdbc-profile context test use Testcontainers, so Docker must be running.

## Commands

On Windows use `gradlew.bat` (or `./gradlew` in Git Bash).

- Build: `./gradlew build`
- Run app (in-memory): `./gradlew bootRun`
- Run app (Postgres): `./gradlew bootRun --args='--spring.profiles.active=jdbc'`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests org.example.aicvgenerator.AiCvGeneratorApplicationTests`
- Single method: `./gradlew test --tests "org.example.aicvgenerator.AiCvGeneratorApplicationTests.methodName"`
- Start local Postgres: `docker compose up -d db`
