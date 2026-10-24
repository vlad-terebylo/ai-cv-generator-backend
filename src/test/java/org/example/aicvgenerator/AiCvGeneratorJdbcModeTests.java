package org.example.aicvgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.aicvgenerator.repository.CvRepository;
import org.example.aicvgenerator.repository.JdbcCvRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The jdbc profile wires PostgreSQL repositories and runs Flyway. */
@SpringBootTest
@ActiveProfiles("jdbc")
@Import(PostgresTestConfig.class)
class AiCvGeneratorJdbcModeTests {

    @Autowired
    CvRepository cvRepository;

    @Test
    void contextLoadsWithJdbcRepositories() {
        assertThat(cvRepository).isInstanceOf(JdbcCvRepository.class);
    }

}
