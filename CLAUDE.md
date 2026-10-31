# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

CV import feature is implemented on `feature/cv-import`: `POST /api/v1/cvs` takes a `.pdf`/`.docx` upload, extracts text (PDFBox / POI), asks Gemini whether it is a CV and to parse it, then upserts it by email into the in-memory repositories. `GET /api/v1/cvs/{id}` returns a stored CV. A non-CV, or a CV missing first name/last name/email, is rejected with 422 and nothing is saved.

**Architecture rules live in `ARCHITECTURE.md` and are binding** (layer-first packages under `org.example.aicvgenerator`, in-memory storage only, MapStruct at each layer boundary). Outbound AI calls go through the `client` package.

## Stack

- Gradle (use the wrapper), Java 17 toolchain, Spring Boot 4.1.1
- Spring MVC, MapStruct, Lombok, springdoc (Swagger UI at `/swagger-ui.html`)
- Storage is in-memory `ArrayList` repositories only; no database, data is lost on restart.
- Config is `application.yaml`. Gemini needs `GEMINI_API_KEY` (optional `GEMINI_MODEL`, default `gemini-2.5-flash`); without it uploads fail with 502.

## Commands

On Windows use `gradlew.bat` (or `./gradlew` in Git Bash).

- Build: `./gradlew build`
- Run app: `./gradlew bootRun`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests org.example.aicvgenerator.AiCvGeneratorApplicationTests`
- Single method: `./gradlew test --tests "org.example.aicvgenerator.AiCvGeneratorApplicationTests.methodName"`
