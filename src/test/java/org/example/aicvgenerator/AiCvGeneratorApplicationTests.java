package org.example.aicvgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.aicvgenerator.repository.CvRepository;
import org.example.aicvgenerator.repository.InMemoryCvRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** In-memory repositories, no database. */
@SpringBootTest
class AiCvGeneratorApplicationTests {

    @Autowired
    CvRepository cvRepository;

    @Autowired
    @Qualifier("cvImportExecutor")
    ThreadPoolTaskExecutor cvImportExecutor;

    @Test
    void contextLoadsWithInMemoryRepositoriesAndNoDatabase() {
        assertThat(cvRepository).isInstanceOf(InMemoryCvRepository.class);
    }

    @Test
    void importExecutorIsBoundedAsConfigured() {
        assertThat(cvImportExecutor.getCorePoolSize()).isEqualTo(4);
        assertThat(cvImportExecutor.getMaxPoolSize()).isEqualTo(4);
        assertThat(cvImportExecutor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(20);
        assertThat(cvImportExecutor.getThreadNamePrefix()).isEqualTo("cv-import-");
    }

}
