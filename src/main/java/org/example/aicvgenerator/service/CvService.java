package org.example.aicvgenerator.service;

import static org.example.aicvgenerator.config.Timing.NANOS_PER_MILLISECOND;

import java.util.List;
import java.util.Locale;

import org.example.aicvgenerator.exception.CvNotFoundException;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.mapper.CvEntityMapper;
import org.example.aicvgenerator.mapper.CvExtractionMapper;
import org.example.aicvgenerator.repository.CvRepository;
import org.example.aicvgenerator.repository.ExperienceRepository;
import org.example.aicvgenerator.repository.UpsertResult;
import org.example.aicvgenerator.repository.entity.CvEntity;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvExtraction;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.example.aicvgenerator.service.model.Experience;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CvService {

    private final DocumentTextExtractor textExtractor;
    private final CvAnalyzer cvAnalyzer;
    private final CvRepository cvRepository;
    private final ExperienceRepository experienceRepository;
    private final CvEntityMapper entityMapper;
    private final CvExtractionMapper extractionMapper;

    public CvSaveResult process(CvDocument document) {
        long start = System.nanoTime();
        String text = textExtractor.extract(document);
        long extracted = System.nanoTime();
        CvExtraction extraction = cvAnalyzer.analyze(text);
        long analysed = System.nanoTime();
        if (!extraction.isCv()) {
            throw new NotACvException("The uploaded document is not a CV");
        }
        Cv cv = extractionMapper.toDomain(extraction);
        if (!StringUtils.hasText(cv.getEmail()) || !StringUtils.hasText(cv.getFirstName())
                || !StringUtils.hasText(cv.getLastName())) {
            throw new NotACvException("The CV must contain a first name, last name and email");
        }
        cv.setEmail(cv.getEmail().strip().toLowerCase(Locale.ROOT));
        cv.setSourceFileName(document.fileName());
        CvSaveResult result = save(cv);
        long saved = System.nanoTime();
        long extractionMs = (extracted - start) / NANOS_PER_MILLISECOND;
        long geminiMs = (analysed - extracted) / NANOS_PER_MILLISECOND;
        long savingMs = (saved - analysed) / NANOS_PER_MILLISECOND;
        log.info("Processed {}: text extraction {} ms, Gemini {} ms, saving {} ms", document.fileName(),
                extractionMs, geminiMs, savingMs);
        return result;
    }

    /** @throws CvNotFoundException if no CV has this id */
    public Cv getById(long id) {
        CvEntity entity = cvRepository.findById(id).orElseThrow(() -> new CvNotFoundException(id));
        return entityMapper.toDomain(entity, experienceRepository.findByCvId(id));
    }

    /** Atomicity under parallel import workers is the repositories' job; see {@code upsertByEmail}. */
    private CvSaveResult save(Cv cv) {
        UpsertResult upserted = cvRepository.upsertByEmail(entityMapper.toEntity(cv));
        cv.setId(upserted.id());
        List<Experience> experience = cv.getExperience() == null ? List.of() : cv.getExperience();
        experienceRepository.replaceByCvId(upserted.id(), entityMapper.toExperienceEntities(experience, upserted.id()));
        return new CvSaveResult(cv, upserted.created());
    }
}
