package org.example.aicvgenerator.repository.entity;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CvEntity {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String summary;
    private String sourceFileName;
    private Instant createdAt;
    private Instant updatedAt;
}
