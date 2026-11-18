package org.example.aicvgenerator.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Experience {

    private Integer startYear;
    /** Null means the position is current. */
    private Integer endYear;
    private String position;
    private String company;
}
