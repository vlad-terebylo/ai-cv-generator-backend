package org.example.aicvgenerator.repository.entity;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class JobEntity {

    private UUID id;
    private String status;
    private String label;
    private String entityType;
    private String entityId;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
