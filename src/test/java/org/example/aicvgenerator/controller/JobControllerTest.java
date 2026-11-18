package org.example.aicvgenerator.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.example.aicvgenerator.exception.JobNotFoundException;
import org.example.aicvgenerator.exception.GlobalExceptionHandler;
import org.example.aicvgenerator.mapper.JobDtoMapperImpl;
import org.example.aicvgenerator.service.JobService;
import org.example.aicvgenerator.service.model.Job;
import org.example.aicvgenerator.service.model.JobStatus;
import org.example.aicvgenerator.service.model.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(JobController.class)
@Import({JobDtoMapperImpl.class, GlobalExceptionHandler.class})
class JobControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean JobService jobService;

    private final UUID id = UUID.randomUUID();

    private Job.JobBuilder job(JobStatus status) {
        return Job.builder().id(id).status(status).label("cv.pdf");
    }

    @Test
    void returnsPendingJob() throws Exception {
        when(jobService.getById(id)).thenReturn(job(JobStatus.PENDING).build());

        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.entityType").doesNotExist())
                .andExpect(jsonPath("$.entityId").doesNotExist());
    }

    @Test
    void returnsCompletedJobWithTheCvAsItsResult() throws Exception {
        when(jobService.getById(id)).thenReturn(job(JobStatus.COMPLETED).entityType(EntityType.CV).entityId("42").build());

        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.entityType").value("CV"))
                .andExpect(jsonPath("$.entityId").value("42"))
                .andExpect(jsonPath("$.cvId").doesNotExist())
                .andExpect(jsonPath("$.fileName").doesNotExist())
                .andExpect(jsonPath("$.name").doesNotExist())
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void returnsFailedJobWithErrorDetails() throws Exception {
        when(jobService.getById(id)).thenReturn(job(JobStatus.FAILED)
                .errorCode("NOT_A_CV").errorMessage("The uploaded document is not a CV").build());

        mockMvc.perform(get("/api/v1/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("NOT_A_CV"))
                .andExpect(jsonPath("$.errorMessage").value("The uploaded document is not a CV"))
                .andExpect(jsonPath("$.entityId").doesNotExist());
    }

    @Test
    void returns404ForUnknownJob() throws Exception {
        when(jobService.getById(id)).thenThrow(new JobNotFoundException(id));

        mockMvc.perform(get("/api/v1/jobs/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void returns400ForMalformedId() throws Exception {
        mockMvc.perform(get("/api/v1/jobs/not-a-uuid")).andExpect(status().isBadRequest());
    }
}
