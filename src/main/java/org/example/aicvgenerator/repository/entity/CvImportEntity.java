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
public class CvImportEntity {

    private UUID id;
    private String status;
    private String fileName;
    private Long cvId;
    private String errorCode;
    private String errorMessage;
    private Instant createdAt;
    private Instant updatedAt;
}
