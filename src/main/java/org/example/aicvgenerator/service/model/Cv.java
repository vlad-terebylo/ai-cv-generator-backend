package org.example.aicvgenerator.service.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cv {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String summary;
    private String sourceFileName;
    private List<Experience> experience;
}
