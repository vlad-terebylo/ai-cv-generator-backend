package org.example.aicvgenerator.service;

import org.example.aicvgenerator.service.model.EntityRef;

/** The work of one background job. It runs on a worker thread and knows nothing about job rows or threads. */
@FunctionalInterface
public interface JobTask {

    /**
     * Does the work and returns the entity it produced.
     *
     * @throws RuntimeException any failure; if it implements {@code JobFailure} the job reports its code and message
     */
    EntityRef run();
}
