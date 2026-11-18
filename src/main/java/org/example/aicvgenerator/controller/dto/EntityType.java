package org.example.aicvgenerator.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** The kinds of entity a job can produce. Add a constant here when a new kind of job is introduced. */
@Schema(description = "Kind of entity a job produced; CV is fetched with GET /api/v1/cvs/{id}")
public enum EntityType {
    CV
}
