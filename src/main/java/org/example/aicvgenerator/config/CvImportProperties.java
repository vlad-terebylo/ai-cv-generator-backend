package org.example.aicvgenerator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/** Size of the background pool that processes uploaded CVs. */
@Validated
@ConfigurationProperties(prefix = "cv.import")
public record CvImportProperties(@Min(1) int poolSize, @Min(0) int queueCapacity) {
}
