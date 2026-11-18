package org.example.aicvgenerator.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.example.aicvgenerator.exception.DuplicateEmailException;
import org.example.aicvgenerator.repository.entity.CvEntity;
import org.example.aicvgenerator.repository.entity.ExperienceEntity;
import org.junit.jupiter.api.Test;
import org.example.aicvgenerator.exception.DuplicateEmailException;

/** Behaviour every repository implementation (currently in-memory) must share. */
abstract class RepositoryContractTest {

    abstract CvRepository cvRepository();

    abstract ExperienceRepository experienceRepository();

    private CvEntity cv(String email) {
        return CvEntity.builder().firstName("Valieria").lastName("Karlova").email(email)
                .summary("proactive").sourceFileName("cv.pdf").build();
    }

    @Test
    void insertsAndFindsByEmailAndId() {
        long id = cvRepository().insert(cv("a@x.com"));

        assertThat(cvRepository().findById(id)).get().satisfies(found -> {
            assertThat(found.getEmail()).isEqualTo("a@x.com");
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getUpdatedAt()).isNotNull();
        });
        assertThat(cvRepository().findByEmail("a@x.com")).get().extracting(CvEntity::getId).isEqualTo(id);
        assertThat(cvRepository().findByEmail("missing@x.com")).isEmpty();
        assertThat(cvRepository().findById(-1)).isEmpty();
    }

    @Test
    void generatesDistinctIds() {
        long first = cvRepository().insert(cv("b1@x.com"));
        long second = cvRepository().insert(cv("b2@x.com"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void updatesRow() {
        long id = cvRepository().insert(cv("c@x.com"));
        CvEntity entity = cv("c@x.com");
        entity.setId(id);
        entity.setSummary("changed");

        cvRepository().update(entity);

        assertThat(cvRepository().findById(id)).get().extracting(CvEntity::getSummary).isEqualTo("changed");
    }

    @Test
    void emailMustBeUnique() {
        cvRepository().insert(cv("d@x.com"));

        assertThatThrownBy(() -> cvRepository().insert(cv("d@x.com"))).isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void upsertInsertsANewEmailAndReplacesAnExistingOne() {
        UpsertResult first = cvRepository().upsertByEmail(cv("u@x.com"));
        CvEntity changed = cv("u@x.com");
        changed.setSummary("changed");
        UpsertResult second = cvRepository().upsertByEmail(changed);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(cvRepository().findById(first.id())).get().extracting(CvEntity::getSummary).isEqualTo("changed");
    }

    @Test
    void storesExperienceNewestFirstAndReplacesItByCvId() {
        long id = cvRepository().insert(cv("e@x.com"));
        experienceRepository().replaceByCvId(id, List.of(
                ExperienceEntity.builder().startYear(2020).endYear(2025).position("Java Developer").company("Nix Orbit").build(),
                ExperienceEntity.builder().startYear(2025).endYear(null).position("Senior Java Developer").company("Absolute").build(),
                ExperienceEntity.builder().position("Intern").build()));

        List<ExperienceEntity> found = experienceRepository().findByCvId(id);
        assertThat(found).extracting(ExperienceEntity::getPosition)
                .containsExactly("Senior Java Developer", "Java Developer", "Intern");
        assertThat(found.get(0).getEndYear()).isNull();
        assertThat(found).extracting(ExperienceEntity::getId).doesNotContainNull();

        experienceRepository().replaceByCvId(id, List.of(ExperienceEntity.builder().position("Lead").build()));
        assertThat(experienceRepository().findByCvId(id)).extracting(ExperienceEntity::getPosition)
                .containsExactly("Lead");

        experienceRepository().replaceByCvId(id, List.of());
        assertThat(experienceRepository().findByCvId(id)).isEmpty();
    }

    @Test
    void replacingExperienceOfOneCvKeepsOthers() {
        long first = cvRepository().insert(cv("f1@x.com"));
        long second = cvRepository().insert(cv("f2@x.com"));
        experienceRepository().replaceByCvId(first, List.of(ExperienceEntity.builder().position("A").build()));
        experienceRepository().replaceByCvId(second, List.of(ExperienceEntity.builder().position("B").build()));

        experienceRepository().replaceByCvId(first, List.of());

        assertThat(experienceRepository().findByCvId(first)).isEmpty();
        assertThat(experienceRepository().findByCvId(second)).hasSize(1);
    }
}
