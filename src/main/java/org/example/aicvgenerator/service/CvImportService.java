package org.example.aicvgenerator.service;

import java.util.UUID;

import org.example.aicvgenerator.exception.CvImportNotFoundException;
import org.example.aicvgenerator.exception.ServerBusyException;
import org.example.aicvgenerator.mapper.CvImportEntityMapper;
import org.example.aicvgenerator.repository.CvImportRepository;
import org.example.aicvgenerator.service.model.CvDocument;
import org.example.aicvgenerator.service.model.CvImport;
import org.example.aicvgenerator.service.model.CvImportStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/** Accepts uploads quickly and hands the slow parsing to the background pool. */
@Service
public class CvImportService {

    static final String SERVER_BUSY = "SERVER_BUSY";

    private final DocumentTextExtractor textExtractor;
    private final CvImportRepository cvImportRepository;
    private final CvImportEntityMapper mapper;
    private final CvImportProcessor processor;
    private final TaskExecutor executor;

    public CvImportService(DocumentTextExtractor textExtractor, CvImportRepository cvImportRepository,
                           CvImportEntityMapper mapper, CvImportProcessor processor,
                           @Qualifier("cvImportExecutor") TaskExecutor executor) {
        this.textExtractor = textExtractor;
        this.cvImportRepository = cvImportRepository;
        this.mapper = mapper;
        this.processor = processor;
        this.executor = executor;
    }

    /** Creates a PENDING job and queues the document; throws {@link ServerBusyException} if the queue is full. */
    public CvImport submit(CvDocument document) {
        textExtractor.requireSupported(document);
        UUID id = UUID.randomUUID();
        cvImportRepository.insert(mapper.toEntity(CvImport.builder()
                .id(id)
                .status(CvImportStatus.PENDING)
                .fileName(document.fileName())
                .build()));
        CvImport created = getById(id);
        try {
            executor.execute(() -> processor.run(id, document));
        } catch (TaskRejectedException e) {
            processor.markFailed(id, SERVER_BUSY, new ServerBusyException().getMessage());
            throw new ServerBusyException();
        }
        return created;
    }

    public CvImport getById(UUID id) {
        return cvImportRepository.findById(id)
                .map(mapper::toDomain)
                .orElseThrow(() -> new CvImportNotFoundException(id));
    }
}
