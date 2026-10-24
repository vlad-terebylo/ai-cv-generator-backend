package org.example.aicvgenerator.repository;

import org.example.aicvgenerator.PostgresTestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@JdbcTest
@ActiveProfiles("jdbc")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcCvRepository.class, JdbcExperienceRepository.class})
class CvRepositoryTest extends RepositoryContractTest {

    @Autowired
    JdbcCvRepository cvRepository;

    @Autowired
    JdbcExperienceRepository experienceRepository;

    @Override
    CvRepository cvRepository() {
        return cvRepository;
    }

    @Override
    ExperienceRepository experienceRepository() {
        return experienceRepository;
    }
}
