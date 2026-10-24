package org.example.aicvgenerator.repository.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceEntity {

    private Long id;
    private Long cvId;
    private Integer startYear;
    private Integer endYear;
    private String position;
    private String company;
}
