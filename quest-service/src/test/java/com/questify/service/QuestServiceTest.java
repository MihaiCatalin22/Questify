package com.questify.service;

import com.questify.domain.Quest;
import com.questify.domain.QuestParticipant;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import com.questify.dto.QuestDtos.CreateQuestReq;
import com.questify.dto.QuestDtos.UpdateQuestReq;
import com.questify.dto.QuestDtos.UpdateQuestStatusReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.QuestParticipantRepository;
import com.questify.repository.QuestRepository;
import com.questify.service.QuestService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class QuestServiceTest {

    @Mock QuestRepository quests;
    @Mock QuestParticipantRepository participants;
    @Mock EventPublisher events;

    @InjectMocks QuestService service;

    private final AtomicLong ids = new AtomicLong(1);

    private static final String TOPIC = "dev.questify.quests";

    @BeforeEach
    void setup() {
        // Inject @Value field
        ReflectionTestUtils.setField(service, "questsTopic", TOPIC);

        // Auto-assign IDs on save
        when(quests.save(any(Quest.class))).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            if (q.getId() == null) q.setId(ids.getAndIncrement());
            return q;
        });
    }

    /* ----------------------- helpers ----------------------- */

    private Quest quest(long id, String owner, QuestStatus status, QuestVisibility vis) {
        Quest q = Quest.builder()
                .id(id)
                .title("Q" + id)
                .description("D" + id)
                .category(com.questify.domain.QuestCategory.OTHER)
                .status(status)
                .startDate(Instant.parse("2025-01-01T00:00:00Z"))
                .endDate(Instant.parse("2025-12-31T23:59:59Z"))
                .visibility(vis)
                .createdByUserId(owner)
                .build();
        return q;
    }

    /* ---------------------------- create ---------------------------- */

    @Test
    void create_happy_sets_defaults_and_emits_event() {
        var req = new CreateQuestReq(
                "Read 20 pages",
                "Daily reading",
                com.questify.domain.QuestCategory.OTHER,
                Instant.parse("2025-01-10T00:00:00Z"),
                Instant.parse("2025-02-10T23:59:59Z"),
                QuestVisibility.PRIVATE,
                "u7"
        );

        Quest out = service.create(req, "u7");

        assertNotNull(out.getId());
        assertThat(out.getStatus()).isEqualTo(QuestStatus.ACTIVE);
        assertThat(out.getCreatedByUserId()).isEqualTo("u7");
        verify(quests).save(argThat(q -> q.getStatus() == QuestStatus.ACTIVE &&
                q.getVisibility() == QuestVisibility.PRIVATE &&
                "u7".equals(q.getCreatedByUserId())));

        verify(events).publish(eq(TOPIC), eq(String.valueOf(out.getId())), eq("QuestCreated"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), out.getId()) &&
                                Objects.equals(m.get("createdByUserId"), "u7") &&
                                Objects.equals(m.get("title"), "Read 20 pages") &&
                                Objects.equals(m.get("status"), QuestStatus.ACTIVE.name())
                ));
    }

    @Test
    void create_denied_when_requester_null() {
        var req = new CreateQuestReq("A","B", com.questify.domain.QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600), QuestVisibility.PUBLIC, "u1");
        assertThrows(AccessDeniedException.class, () -> service.create(req, null));
        verifyNoInteractions(quests, events);
    }

    @Test
    void create_denied_when_mismatch() {
        var req = new CreateQuestReq("A","B", com.questify.domain.QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600), QuestVisibility.PUBLIC, "u1");
        assertThrows(AccessDeniedException.class, () -> service.create(req, "uX"));
        verifyNoInteractions(quests, events);
    }

    /* ---------------------------- get ---------------------------- */

    @Test
    void get_happy() {
        when(quests.findById(9L)).thenReturn(Optional.of(quest(9L, "u1", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        Quest q = service.get(9L);
        assertThat(q.getId()).isEqualTo(9L);
    }

    @Test
    void get_404() {
        when(quests.findById(71L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.get(71L));
    }

    /* ---------------------------- update ---------------------------- */

    @Test
    void update_owner_ok_sets_fields_and_emits_event() {
        when(quests.findById(10L)).thenReturn(Optional.of(quest(10L, "u3", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));
        var req = new UpdateQuestReq(
                "  New Title  ",
                " New Desc ",
                com.questify.domain.QuestCategory.OTHER,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T23:59:59Z"),
                QuestVisibility.PUBLIC
        );

        Quest out = service.update(10L, req, "u3");
        assertNotNull(out);
        assertThat(out.getTitle()).isEqualTo("  New Title  ");
        assertThat(out.getDescription()).isEqualTo(" New Desc ");
        assertThat(out.getVisibility()).isEqualTo(QuestVisibility.PUBLIC);
        verify(quests).save(argThat(q -> q.getId()==10L && q.getVisibility()==QuestVisibility.PUBLIC));
        verify(events).publish(eq(TOPIC), eq("10"), eq("QuestUpdated"), eq(1), eq("quest-service"), anyMap());
    }

    @Test
    void update_denied_when_not_owner() {
        when(quests.findById(10L)).thenReturn(Optional.of(quest(10L, "owner", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));
        var req = new UpdateQuestReq("T","D", com.questify.domain.QuestCategory.OTHER, null, null, QuestVisibility.PUBLIC);
        assertThrows(AccessDeniedException.class, () -> service.update(10L, req, "other"));
        verify(quests, never()).save(any());
        verifyNoInteractions(events);
    }

    /* ---------------------------- updateStatus ---------------------------- */

    @Test
    void updateStatus_owner_ok_emits_event() {
        when(quests.findById(4L)).thenReturn(Optional.of(quest(4L, "u1", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        var req = new UpdateQuestStatusReq(QuestStatus.COMPLETED);
        Quest out = service.updateStatus(4L, req, "u1");
        assertThat(out.getStatus()).isEqualTo(QuestStatus.COMPLETED);
        verify(events).publish(eq(TOPIC), eq("4"), eq("QuestStatusUpdated"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) -> Objects.equals(m.get("status"), QuestStatus.COMPLETED.name())));
    }

    @Test
    void updateStatus_denied_when_not_owner() {
        when(quests.findById(4L)).thenReturn(Optional.of(quest(4L, "u1", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        var req = new UpdateQuestStatusReq(QuestStatus.ARCHIVED);
        assertThrows(AccessDeniedException.class, () -> service.updateStatus(4L, req, "uX"));
        verify(quests, never()).save(any());
        verifyNoInteractions(events);
    }

    /* ---------------------------- archive ---------------------------- */

    @Test
    void archive_owner_ok_sets_archived_and_emits() {
        when(quests.findById(8L)).thenReturn(Optional.of(quest(8L, "u3", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));
        Quest out = service.archive(8L, "u3");
        assertNotNull(out);
        assertThat(out.getStatus()).isEqualTo(QuestStatus.ARCHIVED);
        verify(quests).save(argThat(q -> q.getStatus() == QuestStatus.ARCHIVED));
        verify(events).publish(eq(TOPIC), eq("8"), eq("QuestArchived"), eq(1), eq("quest-service"), anyMap());
    }

    @Test
    void archive_denied_when_not_owner() {
        when(quests.findById(8L)).thenReturn(Optional.of(quest(8L, "owner", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));
        assertThrows(AccessDeniedException.class, () -> service.archive(8L, "other"));
        verify(quests, never()).save(any());
        verifyNoInteractions(events);
    }

    /* ---------------------------- read/list/search passthroughs ---------------------------- */

    @Test
    void listByStatus_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findByStatus(eq(QuestStatus.ACTIVE), any(Pageable.class))).thenReturn(page);
        assertThat(service.listByStatus(QuestStatus.ACTIVE, PageRequest.of(0,10))).isSameAs(page);
    }

    @Test
    void mine_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findByCreatedByUserId(eq("u1"), any(Pageable.class))).thenReturn(page);
        assertThat(service.mine("u1", PageRequest.of(0,10))).isSameAs(page);
    }

    @Test
    void mineOrParticipating_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findMyOrParticipating(eq("u1"), any(Pageable.class))).thenReturn(page);
        assertThat(service.mineOrParticipating("u1", PageRequest.of(0,10))).isSameAs(page);
    }

    @Test
    void discoverActive_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findByVisibilityAndStatus(eq(QuestVisibility.PUBLIC), eq(QuestStatus.ACTIVE), any(Pageable.class))).thenReturn(page);
        assertThat(service.discoverActive(PageRequest.of(0,10))).isSameAs(page);
    }

    @Test
    void searchPublic_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.searchPublic(eq("run"), any(Pageable.class))).thenReturn(page);
        assertThat(service.searchPublic("run", PageRequest.of(0,10))).isSameAs(page);
    }

    /* ---------------------------- join ---------------------------- */

    @Test
    void join_private_denied() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));
        assertThrows(AccessDeniedException.class, () -> service.join(1L, "u5"));
        verifyNoInteractions(participants);
        verifyNoInteractions(events);
    }

    @Test
    void join_idempotent_when_already_participant() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        when(participants.findByQuest_IdAndUserId(1L, "u5")).thenReturn(Optional.of(new QuestParticipant(quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC), "u5")));
        service.join(1L, "u5");
        verify(participants, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void join_saves_and_emits_event() {
        Quest q = quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        when(participants.findByQuest_IdAndUserId(1L, "u5")).thenReturn(Optional.empty());

        service.join(1L, "u5");

        verify(participants).save(argThat(p -> Objects.equals(p.getQuest().getId(), 1L) && Objects.equals(p.getUserId(), "u5")));
        ArgumentCaptor<Map<String, Object>> payloadJoin = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TOPIC), eq("1"), eq("ParticipantJoined"), eq(1), eq("quest-service"), payloadJoin.capture());
        Map<String, Object> mJoin = payloadJoin.getValue();
        assertThat(mJoin.get("questId")).isEqualTo(1L);
        assertThat(mJoin.get("userId")).isEqualTo("u5");
    }

    @Test
    void join_duplicate_violation_swallowed_no_event() {
        Quest q = quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        when(participants.findByQuest_IdAndUserId(1L, "u5")).thenReturn(Optional.empty());
        when(participants.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        assertDoesNotThrow(() -> service.join(1L, "u5"));
        verify(events, never()).publish(anyString(), anyString(), eq("ParticipantJoined"), anyInt(), anyString(), anyMap());
    }

    /* ---------------------------- leave ---------------------------- */

    @Test
    void leave_noop_when_not_participating() {
        when(participants.findByQuest_IdAndUserId(1L, "u5")).thenReturn(Optional.empty());
        service.leave(1L, "u5");
        verify(participants, never()).delete(any());
        verifyNoInteractions(events);
    }

    @Test
    void leave_deletes_and_emits_event() {
        Quest q = quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        QuestParticipant qp = new QuestParticipant(q, "u5");
        when(participants.findByQuest_IdAndUserId(1L, "u5")).thenReturn(Optional.of(qp));

        service.leave(1L, "u5");

        verify(participants).delete(eq(qp));
        ArgumentCaptor<Map<String, Object>> payloadLeft = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TOPIC), eq("1"), eq("ParticipantLeft"), eq(1), eq("quest-service"), payloadLeft.capture());
        Map<String, Object> mLeft = payloadLeft.getValue();
        assertThat(mLeft.get("questId")).isEqualTo(1L);
        assertThat(mLeft.get("userId")).isEqualTo("u5");
    }

    /* ---------------------------- misc ---------------------------- */

    @Test
    void participantsCount_returns_int() {
        when(participants.countByQuest_Id(99L)).thenReturn(42L);
        assertThat(service.participantsCount(99L)).isEqualTo(42);
    }

    @Test
    void isOwnerOrParticipant_true_for_owner() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "u7", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        assertTrue(service.isOwnerOrParticipant(1L, "u7"));
    }

    @Test
    void isOwnerOrParticipant_true_for_participant() {
        Quest q = quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        when(participants.findByQuest_IdAndUserId(1L, "u5")).thenReturn(Optional.of(new QuestParticipant(q, "u5")));
        assertTrue(service.isOwnerOrParticipant(1L, "u5"));
    }

    @Test
    void isOwnerOrParticipant_false_when_not_found() {
        when(quests.findById(404L)).thenReturn(Optional.empty());
        assertFalse(service.isOwnerOrParticipant(404L, "u1"));
    }

    @Test
    void isOwnerOrParticipant_false_otherwise() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        when(participants.findByQuest_IdAndUserId(1L, "u9")).thenReturn(Optional.empty());
        assertFalse(service.isOwnerOrParticipant(1L, "u9"));
    }
}