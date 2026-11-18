package org.example.aicvgenerator.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.aicvgenerator.repository.entity.CvEntity;
import org.junit.jupiter.api.Test;

class InMemoryRepositoryTest extends RepositoryContractTest {

    private final InMemoryCvRepository cvRepository = new InMemoryCvRepository();
    private final InMemoryExperienceRepository experienceRepository = new InMemoryExperienceRepository();

    @Override
    CvRepository cvRepository() {
        return cvRepository;
    }

    @Override
    ExperienceRepository experienceRepository() {
        return experienceRepository;
    }

    @Test
    void returnedEntitiesAreCopies() {
        long id = cvRepository.insert(CvEntity.builder().firstName("A").lastName("B").email("copy@x.com").build());

        cvRepository.findById(id).orElseThrow().setFirstName("mutated");

        assertThat(cvRepository.findById(id).orElseThrow().getFirstName()).isEqualTo("A");
    }
}
