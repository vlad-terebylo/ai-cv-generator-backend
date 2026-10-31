package org.example.aicvgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.aicvgenerator.repository.CvRepository;
import org.example.aicvgenerator.repository.InMemoryCvRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** In-memory repositories, no database. */
@SpringBootTest
class AiCvGeneratorApplicationTests {

    @Autowired
    CvRepository cvRepository;

    @Test
    void contextLoadsWithInMemoryRepositoriesAndNoDatabase() {
        assertThat(cvRepository).isInstanceOf(InMemoryCvRepository.class);
    }

}
