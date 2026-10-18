package com.graphchart.graphapi.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphchart.graphapi.service.HierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GraphController.class)
class GraphControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HierarchyService hierarchyService;

    @Test
    void createsUniversity() throws Exception {
        UUID id = UUID.randomUUID();
        when(hierarchyService.create(eq("universities"), any(EntityRequest.class)))
                .thenReturn(new EntityResponse(id, "universities", "Demo University", null, null, null, true, null, null));

        mvc.perform(post("/api/universities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EntityRequest("Demo University", null, null, null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Demo University"));
    }

    @Test
    void listsEntities() throws Exception {
        when(hierarchyService.list("machines")).thenReturn(List.of());

        mvc.perform(get("/api/machines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
