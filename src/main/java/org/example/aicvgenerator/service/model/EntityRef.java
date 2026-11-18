package org.example.aicvgenerator.service.model;

/** Points at the entity a finished job produced; the job never carries the entity itself. */
public record EntityRef(EntityType type, String id) {
}
