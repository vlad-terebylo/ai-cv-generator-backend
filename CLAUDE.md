# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

Early-stage Spring Boot skeleton (`org.example.aicvgenerator`). Only the application entry point, a context-load test, and `spring.application.name` exist so far — there is no controller, service, persistence, or AI integration code yet. Update this file as the architecture takes shape.

## Stack

- Gradle (use the wrapper), Java 17 toolchain, Spring Boot 4.1.1
- Lombok (annotation processor configured for main and test)
- Only `spring-boot-starter` is a dependency today; web, JPA/JDBC, and Postgres driver have not been added yet.
- `docker-compose.yaml` defines a Postgres service (db `ai_cv_generator`, port 5432) intended as the future database. It is not yet wired into `application.properties`.

## Commands

On Windows use `gradlew.bat` (or `./gradlew` in Git Bash).

- Build: `./gradlew build`
- Run app: `./gradlew bootRun`
- All tests: `./gradlew test`
- Single test class: `./gradlew test --tests org.example.aicvgenerator.AiCvGeneratorApplicationTests`
- Single method: `./gradlew test --tests "org.example.aicvgenerator.AiCvGeneratorApplicationTests.methodName"`
- Start local Postgres: `docker compose up -d db`
