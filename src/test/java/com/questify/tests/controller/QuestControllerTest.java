package com.questify.tests.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.questify.Main;
import com.questify.domain.QuestStatus;
import com.questify.dto.QuestDtos;
import com.questify.service.QuestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Main.class)
@AutoConfigureMockMvc(addFilters = false)
class QuestControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @MockitoBean QuestService service;

    QuestDtos.QuestRes sample;

    @BeforeEach
    void setup() {
        sample = new QuestDtos.QuestRes(1L, "Quest Title", "Quest Desc", QuestStatus.ACTIVE, 10L);
    }

    @Test
    void create_201_and_body() throws Exception {
        var req = new QuestDtos.CreateQuestReq("Quest Title", "Quest Desc", 10L);
        when(service.create(any())).thenReturn(sample);

        mvc.perform(post("/quests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Quest Title"));
    }


    @Test
    void list_quests_returns_page() throws Exception {
        var list = new PageImpl<>(List.of(sample));
        when(service.list(any(), any(PageRequest.class))).thenReturn(list);

        mvc.perform(get("/quests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void update_status_returns_updated() throws Exception {
        var req = new QuestDtos.UpdateQuestStatusReq(QuestStatus.ARCHIVED);
        var updated = new QuestDtos.QuestRes(1L, "Quest Title", "Quest Desc", QuestStatus.ARCHIVED, 10L);

        when(service.updateStatus(eq(1L), eq(QuestStatus.ARCHIVED))).thenReturn(updated);

        mvc.perform(patch("/quests/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
    }

    @Test
    void archive_returns_quest() throws Exception {
        when(service.archive(1L)).thenReturn(sample);

        mvc.perform(post("/quests/1/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }
}
