package com.questify.tests.service;

import com.questify.config.NotFoundException;
import com.questify.domain.Quest;
import com.questify.domain.User;
import com.questify.domain.QuestStatus;
import com.questify.dto.QuestDtos.CreateQuestReq;
import com.questify.dto.QuestDtos.QuestRes;
import com.questify.mapper.QuestMapper;
import com.questify.persistence.QuestRepository;
import com.questify.persistence.UserRepository;
import com.questify.service.QuestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class QuestServiceTest {

    @Mock QuestRepository quests;
    @Mock UserRepository users;
    @Mock QuestMapper mapper;
    @InjectMocks
    QuestService service;

    @Test
    void create_ok_setsCreator_and_ACTIVE_and_maps() {
        CreateQuestReq req = new CreateQuestReq("Read 20 pages", "Daily reading", 7L);

        User creator = new User();
        creator.setId(7L);

        Quest mapped = new Quest();
        mapped.setTitle("Read 20 pages");
        mapped.setDescription("Daily reading");

        Quest saved = new Quest();
        saved.setId(22L);
        saved.setTitle("Read 20 pages");
        saved.setDescription("Daily reading");
        saved.setStatus(QuestStatus.ACTIVE);
        saved.setCreatedBy(creator);

        QuestRes res = new QuestRes(22L, "Read 20 pages", "Daily reading", QuestStatus.ACTIVE, 7L);

        when(users.findById(7L)).thenReturn(Optional.of(creator));
        when(mapper.toEntity(req)).thenReturn(mapped);
        when(quests.save(any(Quest.class))).thenReturn(saved);
        when(mapper.toRes(saved)).thenReturn(res);

        QuestRes out = service.create(req);

        assertThat(out.id()).isEqualTo(22L);
        verify(quests).save(argThat(q ->
                q.getCreatedBy() == creator && q.getStatus() == QuestStatus.ACTIVE));
    }

    @Test
    void create_missingUser_throwsNotFound() {
        CreateQuestReq req = new CreateQuestReq("Run", "5k", 999L);
        when(users.findById(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.create(req));
        verifyNoInteractions(mapper);
        verify(quests, never()).save(any());
    }

    @Test
    void list_withStatus_filters() {
        when(quests.findByStatus(eq(QuestStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var page = service.list(QuestStatus.ACTIVE, PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void list_withoutStatus_returnsAll() {
        when(quests.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        var page = service.list(null, PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void listByCreator_returnsOnlyThatUsersQuests() {
        when(quests.findByCreatedBy_Id(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var page = service.listByCreator(5L, PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void search_titleContains_ignoreCase() {
        when(quests.findByTitleContainingIgnoreCase(eq("run"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var page = service.search("run", PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void search_blankQuery_returnsEmptyPage() {
        var page = service.search("  ", PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
        verifyNoInteractions(quests);
    }

    @Test
    void getOrThrow_404_whenMissing() {
        when(quests.findById(71L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getOrThrow(71L));
    }

    @Test
    void updateStatus_transitions_and_maps() {
        User creator = new User(); creator.setId(1L);
        Quest q = new Quest();
        q.setId(3L); q.setTitle("A"); q.setDescription("B"); q.setStatus(QuestStatus.ACTIVE);
        q.setCreatedBy(creator);

        QuestRes mapped = new QuestRes(3L, "A", "B", QuestStatus.COMPLETED, 1L);

        when(quests.findById(3L)).thenReturn(Optional.of(q));
        when(mapper.toRes(q)).thenReturn(mapped);

        QuestRes out = service.updateStatus(3L, QuestStatus.COMPLETED);

        assertThat(out.status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(q.getStatus()).isEqualTo(QuestStatus.COMPLETED);
        verify(quests, never()).save(any()); // relying on JPA dirty checking in real run
    }

    @Test
    void updateStatus_missing_throwsNotFound() {
        when(quests.findById(404L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.updateStatus(404L, QuestStatus.ARCHIVED));
    }

    @Test
    void archive_setsArchived() {
        User creator = new User(); creator.setId(1L);
        Quest q = new Quest(); q.setId(8L); q.setStatus(QuestStatus.ACTIVE); q.setCreatedBy(creator);

        when(quests.findById(8L)).thenReturn(Optional.of(q));
        when(mapper.toRes(q)).thenReturn(new QuestRes(8L, null, null, QuestStatus.ARCHIVED, 1L));

        QuestRes out = service.archive(8L);
        assertThat(out.status()).isEqualTo(QuestStatus.ARCHIVED);
        assertThat(q.getStatus()).isEqualTo(QuestStatus.ARCHIVED);
    }
}
