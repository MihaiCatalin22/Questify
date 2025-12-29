package com.questify.service;

import com.questify.domain.*;
import com.questify.dto.QuestDtos.CreateQuestReq;
import com.questify.dto.QuestDtos.UpdateQuestReq;
import com.questify.dto.QuestDtos.UpdateQuestStatusReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.QuestParticipantRepository;
import com.questify.repository.QuestRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.*;
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
        ReflectionTestUtils.setField(service, "questsTopic", TOPIC);

        lenient().when(quests.save(any(Quest.class))).thenAnswer(inv -> {
            Quest q = inv.getArgument(0);
            if (q.getId() == null) q.setId(ids.getAndIncrement());
            return q;
        });
    }

    /* ----------------------- helpers ----------------------- */

    private Quest quest(long id, String owner, QuestStatus status, QuestVisibility vis) {
        return Quest.builder()
                .id(id)
                .title("Q" + id)
                .description("D" + id)
                .category(QuestCategory.OTHER)
                .status(status)
                .startDate(Instant.parse("2025-01-01T00:00:00Z"))
                .endDate(Instant.parse("2025-12-31T23:59:59Z"))
                .visibility(vis)
                .createdByUserId(owner)
                .build();
    }

    /* =========================================================================================
     * CREATE
     * ========================================================================================= */

    @Test
    void create_happy_sets_defaults_and_emits_event() {
        var req = new CreateQuestReq(
                "Read 20 pages",
                "Daily reading",
                QuestCategory.OTHER,
                Instant.parse("2025-01-10T00:00:00Z"),
                Instant.parse("2025-02-10T23:59:59Z"),
                QuestVisibility.PRIVATE,
                "u7"
        );

        Quest out = service.create(req, "u7");

        assertThat(out.getId()).isNotNull();
        assertThat(out.getStatus()).isEqualTo(QuestStatus.ACTIVE);
        assertThat(out.getCreatedByUserId()).isEqualTo("u7");

        verify(quests).save(argThat(q ->
                q.getStatus() == QuestStatus.ACTIVE &&
                        q.getVisibility() == QuestVisibility.PRIVATE &&
                        "u7".equals(q.getCreatedByUserId()) &&
                        "Read 20 pages".equals(q.getTitle())
        ));

        verify(events).publish(
                eq(TOPIC),
                eq(String.valueOf(out.getId())),
                eq("QuestCreated"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), out.getId()) &&
                                Objects.equals(m.get("createdByUserId"), "u7") &&
                                Objects.equals(m.get("title"), "Read 20 pages") &&
                                Objects.equals(m.get("status"), QuestStatus.ACTIVE.name()) &&
                                Objects.equals(m.get("visibility"), QuestVisibility.PRIVATE.name())
                )
        );
    }

    @Test
    void create_denied_when_requester_null() {
        var req = new CreateQuestReq(
                "A", "B", QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600),
                QuestVisibility.PUBLIC, "u1"
        );

        assertThatThrownBy(() -> service.create(req, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("only create quests for yourself");

        verifyNoInteractions(quests, participants, events);
    }

    @Test
    void create_denied_when_requester_mismatch() {
        var req = new CreateQuestReq(
                "A", "B", QuestCategory.OTHER,
                Instant.now(), Instant.now().plusSeconds(3600),
                QuestVisibility.PUBLIC, "u1"
        );

        assertThatThrownBy(() -> service.create(req, "uX"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("only create quests for yourself");

        verifyNoInteractions(quests, participants, events);
    }

    /* =========================================================================================
     * GET
     * ========================================================================================= */

    @Test
    void get_happy() {
        when(quests.findById(9L)).thenReturn(Optional.of(quest(9L, "u1", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        Quest q = service.get(9L);
        assertThat(q.getId()).isEqualTo(9L);
    }

    @Test
    void get_404() {
        when(quests.findById(71L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(71L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Quest 71 not found");
    }

    /* =========================================================================================
     * UPDATE
     * ========================================================================================= */

    @Test
    void update_owner_ok_sets_fields_and_emits_event() {
        when(quests.findById(10L)).thenReturn(Optional.of(quest(10L, "u3", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));

        var req = new UpdateQuestReq(
                "New Title",
                "New Desc",
                QuestCategory.OTHER,
                Instant.parse("2025-01-02T00:00:00Z"),
                Instant.parse("2025-12-30T23:59:59Z"),
                QuestVisibility.PUBLIC
        );

        Quest out = service.update(10L, req, "u3");

        assertThat(out.getId()).isEqualTo(10L);
        assertThat(out.getTitle()).isEqualTo("New Title");
        assertThat(out.getDescription()).isEqualTo("New Desc");
        assertThat(out.getVisibility()).isEqualTo(QuestVisibility.PUBLIC);

        verify(quests).save(argThat(q ->
                q.getId() == 10L &&
                        "New Title".equals(q.getTitle()) &&
                        q.getVisibility() == QuestVisibility.PUBLIC
        ));

        verify(events).publish(
                eq(TOPIC), eq("10"),
                eq("QuestUpdated"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), 10L) &&
                                Objects.equals(m.get("title"), "New Title") &&
                                Objects.equals(m.get("visibility"), QuestVisibility.PUBLIC.name())
                )
        );
    }

    @Test
    void update_denied_when_not_owner() {
        when(quests.findById(10L)).thenReturn(Optional.of(quest(10L, "owner", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));
        var req = new UpdateQuestReq(
                "T", "D", QuestCategory.OTHER,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-02T00:00:00Z"),
                QuestVisibility.PUBLIC
        );

        assertThatThrownBy(() -> service.update(10L, req, "other"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only owner");

        verify(quests, never()).save(any());
        verifyNoInteractions(events);
    }

    /* =========================================================================================
     * UPDATE STATUS
     * ========================================================================================= */

    @Test
    void updateStatus_owner_ok_emits_event() {
        when(quests.findById(4L)).thenReturn(Optional.of(quest(4L, "u1", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));

        var req = new UpdateQuestStatusReq(QuestStatus.COMPLETED);
        Quest out = service.updateStatus(4L, req, "u1");

        assertThat(out.getStatus()).isEqualTo(QuestStatus.COMPLETED);

        verify(events).publish(
                eq(TOPIC), eq("4"),
                eq("QuestStatusUpdated"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), 4L) &&
                                Objects.equals(m.get("status"), QuestStatus.COMPLETED.name())
                )
        );
    }

    @Test
    void updateStatus_denied_when_not_owner() {
        when(quests.findById(4L)).thenReturn(Optional.of(quest(4L, "u1", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));

        var req = new UpdateQuestStatusReq(QuestStatus.ARCHIVED);

        assertThatThrownBy(() -> service.updateStatus(4L, req, "uX"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only owner");

        verify(quests, never()).save(any());
        verifyNoInteractions(events);
    }

    /* =========================================================================================
     * ARCHIVE
     * ========================================================================================= */

    @Test
    void archive_owner_ok_sets_archived_and_emits() {
        when(quests.findById(8L)).thenReturn(Optional.of(quest(8L, "u3", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));

        Quest out = service.archive(8L, "u3");

        assertThat(out.getStatus()).isEqualTo(QuestStatus.ARCHIVED);

        verify(quests).save(argThat(q -> q.getId() == 8L && q.getStatus() == QuestStatus.ARCHIVED));
        verify(events).publish(
                eq(TOPIC), eq("8"),
                eq("QuestArchived"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), 8L) &&
                                Objects.equals(m.get("status"), QuestStatus.ARCHIVED.name())
                )
        );
    }

    @Test
    void archive_denied_when_not_owner() {
        when(quests.findById(8L)).thenReturn(Optional.of(quest(8L, "owner", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));

        assertThatThrownBy(() -> service.archive(8L, "other"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only owner");

        verify(quests, never()).save(any());
        verifyNoInteractions(events);
    }

    /* =========================================================================================
     * READ/LIST/SEARCH PASSTHROUGHS
     * ========================================================================================= */

    @Test
    void listByStatus_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findByStatus(eq(QuestStatus.ACTIVE), eq(pageable))).thenReturn(page);

        assertThat(service.listByStatus(QuestStatus.ACTIVE, pageable)).isSameAs(page);
    }

    @Test
    void mine_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findByCreatedByUserId(eq("u1"), eq(pageable))).thenReturn(page);

        assertThat(service.mine("u1", pageable)).isSameAs(page);
    }

    @Test
    void mineOrParticipating_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findMyOrParticipating(eq("u1"), eq(pageable))).thenReturn(page);

        assertThat(service.mineOrParticipating("u1", pageable)).isSameAs(page);
    }

    @Test
    void discoverActive_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findByVisibilityAndStatus(eq(QuestVisibility.PUBLIC), eq(QuestStatus.ACTIVE), eq(pageable)))
                .thenReturn(page);

        assertThat(service.discoverActive(pageable)).isSameAs(page);
    }

    @Test
    void searchPublic_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.searchPublic(eq("run"), eq(pageable))).thenReturn(page);

        assertThat(service.searchPublic("run", pageable)).isSameAs(page);
    }

    @Test
    void mineByStatus_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findByCreatedByUserIdAndStatus(eq("u1"), eq(QuestStatus.ACTIVE), eq(pageable))).thenReturn(page);

        assertThat(service.mineByStatus("u1", QuestStatus.ACTIVE, pageable)).isSameAs(page);
    }

    @Test
    void mineOrParticipatingWithStatus_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findMyOrParticipatingWithStatus(eq("u1"), eq(QuestStatus.ARCHIVED), eq(pageable))).thenReturn(page);

        assertThat(service.mineOrParticipatingWithStatus("u1", QuestStatus.ARCHIVED, pageable)).isSameAs(page);
    }

    @Test
    void mineOrParticipatingNotStatus_passthrough() {
        Page<Quest> page = new PageImpl<>(List.of());
        Pageable pageable = PageRequest.of(0, 10);

        when(quests.findMyOrParticipatingNotStatus(eq("u1"), eq(QuestStatus.ARCHIVED), eq(pageable))).thenReturn(page);

        assertThat(service.mineOrParticipatingNotStatus("u1", QuestStatus.ARCHIVED, pageable)).isSameAs(page);
    }

    /* =========================================================================================
     * FILTERED LISTING + COUNTS
     * ========================================================================================= */

    @Test
    void mineOrParticipatingFiltered_null_archived_calls_unfiltered() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findMyOrParticipating("u1", pageable)).thenReturn(page);

        assertThat(service.mineOrParticipatingFiltered("u1", null, pageable)).isSameAs(page);

        verify(quests).findMyOrParticipating("u1", pageable);
        verify(quests, never()).findMyOrParticipatingWithStatus(anyString(), any(), any());
        verify(quests, never()).findMyOrParticipatingNotStatus(anyString(), any(), any());
    }

    @Test
    void mineOrParticipatingFiltered_archived_true_calls_with_status_archived() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findMyOrParticipatingWithStatus("u1", QuestStatus.ARCHIVED, pageable)).thenReturn(page);

        assertThat(service.mineOrParticipatingFiltered("u1", true, pageable)).isSameAs(page);

        verify(quests).findMyOrParticipatingWithStatus("u1", QuestStatus.ARCHIVED, pageable);
        verify(quests, never()).findMyOrParticipatingNotStatus(anyString(), any(), any());
    }

    @Test
    void mineOrParticipatingFiltered_archived_false_calls_not_status_archived() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Quest> page = new PageImpl<>(List.of());
        when(quests.findMyOrParticipatingNotStatus("u1", QuestStatus.ARCHIVED, pageable)).thenReturn(page);

        assertThat(service.mineOrParticipatingFiltered("u1", false, pageable)).isSameAs(page);

        verify(quests).findMyOrParticipatingNotStatus("u1", QuestStatus.ARCHIVED, pageable);
        verify(quests, never()).findMyOrParticipatingWithStatus(anyString(), any(), any());
    }

    @Test
    void countMineOrParticipatingFiltered_null_calls_unfiltered_count() {
        when(quests.countMyOrParticipating("u1")).thenReturn(123L);

        assertThat(service.countMineOrParticipatingFiltered("u1", null)).isEqualTo(123L);

        verify(quests).countMyOrParticipating("u1");
        verify(quests, never()).countMyOrParticipatingWithStatus(anyString(), any());
        verify(quests, never()).countMyOrParticipatingNotStatus(anyString(), any());
    }

    @Test
    void countMineOrParticipatingFiltered_archived_true_calls_with_status() {
        when(quests.countMyOrParticipatingWithStatus("u1", QuestStatus.ARCHIVED)).thenReturn(9L);

        assertThat(service.countMineOrParticipatingFiltered("u1", true)).isEqualTo(9L);

        verify(quests).countMyOrParticipatingWithStatus("u1", QuestStatus.ARCHIVED);
        verify(quests, never()).countMyOrParticipatingNotStatus(anyString(), any());
    }

    @Test
    void countMineOrParticipatingFiltered_archived_false_calls_not_status() {
        when(quests.countMyOrParticipatingNotStatus("u1", QuestStatus.ARCHIVED)).thenReturn(7L);

        assertThat(service.countMineOrParticipatingFiltered("u1", false)).isEqualTo(7L);

        verify(quests).countMyOrParticipatingNotStatus("u1", QuestStatus.ARCHIVED);
        verify(quests, never()).countMyOrParticipatingWithStatus(anyString(), any());
    }

    /* =========================================================================================
     * JOIN
     * ========================================================================================= */

    @Test
    void join_private_denied() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PRIVATE)));

        assertThatThrownBy(() -> service.join(1L, "u5"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("private");

        verifyNoInteractions(participants);
        verifyNoInteractions(events);
    }

    @Test
    void join_idempotent_when_already_participant() {
        Quest q = quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        when(participants.findByQuest_IdAndUserId(1L, "u5"))
                .thenReturn(Optional.of(new QuestParticipant(q, "u5")));

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

        verify(participants).save(argThat(p ->
                p.getQuest() != null &&
                        Objects.equals(p.getQuest().getId(), 1L) &&
                        Objects.equals(p.getUserId(), "u5")
        ));

        verify(events).publish(
                eq(TOPIC), eq("1"),
                eq("ParticipantJoined"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), 1L) &&
                                Objects.equals(m.get("userId"), "u5")
                )
        );
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

    /* =========================================================================================
     * LEAVE
     * ========================================================================================= */

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

        verify(events).publish(
                eq(TOPIC), eq("1"),
                eq("ParticipantLeft"), eq(1), eq("quest-service"),
                argThat((Map<String, Object> m) ->
                        Objects.equals(m.get("questId"), 1L) &&
                                Objects.equals(m.get("userId"), "u5")
                )
        );
    }

    /* =========================================================================================
     * MISC
     * ========================================================================================= */

    @Test
    void participantsCount_returns_int() {
        when(participants.countByQuest_Id(99L)).thenReturn(42L);
        assertThat(service.participantsCount(99L)).isEqualTo(42);
    }

    @Test
    void participantsCount_throws_on_overflow() {
        when(participants.countByQuest_Id(99L)).thenReturn((long) Integer.MAX_VALUE + 1L);

        assertThatThrownBy(() -> service.participantsCount(99L))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void isOwnerOrParticipant_true_for_owner_does_not_query_participants() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "u7", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));

        assertTrue(service.isOwnerOrParticipant(1L, "u7"));
        verify(participants, never()).findByQuest_IdAndUserId(anyLong(), anyString());
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
        verifyNoInteractions(participants);
    }

    @Test
    void isOwnerOrParticipant_false_otherwise() {
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, "owner", QuestStatus.ACTIVE, QuestVisibility.PUBLIC)));
        when(participants.findByQuest_IdAndUserId(1L, "u9")).thenReturn(Optional.empty());

        assertFalse(service.isOwnerOrParticipant(1L, "u9"));
    }
}
