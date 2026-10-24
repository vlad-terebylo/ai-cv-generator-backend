package org.example.aicvgenerator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.example.aicvgenerator.exception.CvNotFoundException;
import org.example.aicvgenerator.exception.GlobalExceptionHandler;
import org.example.aicvgenerator.exception.NotACvException;
import org.example.aicvgenerator.exception.UnsupportedDocumentException;
import org.example.aicvgenerator.mapper.CvDtoMapperImpl;
import org.example.aicvgenerator.service.CvService;
import org.example.aicvgenerator.service.model.Cv;
import org.example.aicvgenerator.service.model.CvSaveResult;
import org.example.aicvgenerator.service.model.Experience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CvController.class)
@Import({CvDtoMapperImpl.class, GlobalExceptionHandler.class})
class CvControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean CvService cvService;

    private final MockMultipartFile file = new MockMultipartFile("file", "cv.pdf", "application/pdf", new byte[] {1});

    private Cv cv() {
        return Cv.builder().id(1L).firstName("Valieria").lastName("Karlova").email("v.karlova@gmail.com")
                .summary("proactive").experience(List.of(
                        Experience.builder().startYear(2025).position("Senior Java Developer").company("Absolute").build()))
                .build();
    }

    @Test
    void uploadReturns201ForNewCv() throws Exception {
        when(cvService.process(any())).thenReturn(new CvSaveResult(cv(), true));

        mockMvc.perform(multipart("/api/v1/cvs").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Valieria"))
                .andExpect(jsonPath("$.experience[0].company").value("Absolute"));
    }

    @Test
    void uploadReturns200WhenExistingCvUpdated() throws Exception {
        when(cvService.process(any())).thenReturn(new CvSaveResult(cv(), false));

        mockMvc.perform(multipart("/api/v1/cvs").file(file)).andExpect(status().isOk());
    }

    @Test
    void uploadReturns422WhenNotACv() throws Exception {
        when(cvService.process(any())).thenThrow(new NotACvException("not a cv"));

        mockMvc.perform(multipart("/api/v1/cvs").file(file)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void uploadReturns400ForUnsupportedFile() throws Exception {
        when(cvService.process(any())).thenThrow(new UnsupportedDocumentException("nope"));

        mockMvc.perform(multipart("/api/v1/cvs").file(file)).andExpect(status().isBadRequest());
    }

    @Test
    void uploadReturns400WhenFilePartMissing() throws Exception {
        mockMvc.perform(multipart("/api/v1/cvs")).andExpect(status().isBadRequest());
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(cvService.getById(5L)).thenThrow(new CvNotFoundException(5L));

        mockMvc.perform(get("/api/v1/cvs/5")).andExpect(status().isNotFound());
    }
}
