package org.example.aicvgenerator.service;

import java.util.List;
import java.util.Locale;

import org.example.aicvgenerator.client.CvExtraction;
import org.example.aicvgenerator.client.GeminiClient;
import org.example.aicvgenerator.exception.CvNotFoundException;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.mapper.CvEntityMapper;
import org.example.aicvgenerator.mapper.CvExtractionMapper;
import org.example.aicvgenerator.repository.CvRepository;
import org.example.aicvgenerator.repository.ExperienceRepository;
import org.example.aicvgenerator.repository.entity.CvEntity;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CvService {

    private final DocumentTextExtractor textExtractor;
    private final GeminiClient geminiClient;
    private final CvRepository cvRepository;
    private final ExperienceRepository experienceRepository;
    private final CvEntityMapper entityMapper;
    private final CvExtractionMapper extractionMapper;

    /** Parses the document and stores it; an existing CV with the same email is replaced. */
    @Transactional
    public CvSaveResult process(CvDocument document) {
        String text = textExtractor.extract(document);
        CvExtraction extraction = geminiClient.extract(text);
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
        return save(cv);
    }

    @Transactional(readOnly = true)
    public Cv getById(long id) {
        CvEntity entity = cvRepository.findById(id).orElseThrow(() -> new CvNotFoundException(id));
        return entityMapper.toDomain(entity, experienceRepository.findByCvId(id));
    }

    private CvSaveResult save(Cv cv) {
        var existing = cvRepository.findByEmail(cv.getEmail());
        long id;
        if (existing.isPresent()) {
            id = existing.get().getId();
            cv.setId(id);
            cvRepository.update(entityMapper.toEntity(cv));
            experienceRepository.deleteByCvId(id);
        } else {
            id = cvRepository.insert(entityMapper.toEntity(cv));
            cv.setId(id);
        }
        experienceRepository.insertAll(entityMapper.toExperienceEntities(
                cv.getExperience() == null ? List.of() : cv.getExperience(), id));
        return new CvSaveResult(cv, existing.isEmpty());
    }
}
