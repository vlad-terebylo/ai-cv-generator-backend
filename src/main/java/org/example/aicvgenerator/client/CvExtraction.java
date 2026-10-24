package org.example.aicvgenerator.client;

import java.util.List;

/** What the AI returned for a document: whether it is a CV and, if so, its structured content. */
public record CvExtraction(
        boolean isCv,
        String firstName,
        String lastName,
        String email,
        String summary,
        List<ExtractedExperience> experience) {

    public record ExtractedExperience(Integer startYear, Integer endYear, String position, String company) {
    }
}
