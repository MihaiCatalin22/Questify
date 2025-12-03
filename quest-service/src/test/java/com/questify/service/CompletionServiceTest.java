package com.questify.service;

import com.questify.domain.QuestCompletion;
import com.questify.domain.QuestStatus;
import com.questify.kafka.EventPublisher;
import com.questify.repository.QuestCompletionRepository;
import com.questify.service.CompletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class CompletionServiceTest {

    @Mock QuestCompletionRepository completions;
    @Mock EventPublisher events;

    @InjectMocks CompletionService service;

    private static final String TOPIC = "dev.questify.streaks";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "streaksTopic", TOPIC);
        when(completions.save(any(QuestCompletion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    /* ---------------------------- upsertCompleted ---------------------------- */

    @Test
    void upsertCompleted_creates_when_missing_sets_fields_and_emits() {
        when(completions.findByQuestIdAndUserId(5L, "u1")).thenReturn(Optional.empty());

        Instant before = Instant.now();
        QuestCompletion saved = service.upsertCompleted(5L, "u1", 99L);
        Instant after = Instant.now();

        assertThat(saved.getQuestId()).isEqualTo(5L);
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getSubmissionId()).isEqualTo(99L);
        assertThat(saved.getStatus()).isEqualTo(QuestStatus.COMPLETED);
        assertNotNull(saved.getCompletedAt());
        assertThat(!saved.getCompletedAt().isBefore(before) && !saved.getCompletedAt().isAfter(after)).isTrue();

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TOPIC), eq("5"), eq("QuestCompleted"), eq(1), eq("quest-service"), cap.capture());
        Map<String, Object> m = cap.getValue();
        assertThat(m.get("questId")).isEqualTo(5L);
        assertThat(m.get("userId")).isEqualTo("u1");
        assertThat(m.get("submissionId")).isEqualTo(99L);
        assertThat(m.get("completedAt")).isInstanceOf(Instant.class);
    }

    @Test
    void upsertCompleted_updates_when_existing_sets_completed_and_overwrites_submissionId_and_emits() {
        QuestCompletion existing = QuestCompletion.builder()
                .questId(5L).userId("u1").submissionId(111L)
                .status(QuestStatus.ACTIVE)
                .completedAt(null)
                .build();
        when(completions.findByQuestIdAndUserId(5L, "u1")).thenReturn(Optional.of(existing));

        QuestCompletion out = service.upsertCompleted(5L, "u1", 222L);

        assertThat(out.getStatus()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(out.getSubmissionId()).isEqualTo(222L);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TOPIC), eq("5"), eq("QuestCompleted"), eq(1), eq("quest-service"), cap.capture());
        Map<String, Object> m = cap.getValue();
        assertThat(m.get("questId")).isEqualTo(5L);
        assertThat(m.get("userId")).isEqualTo("u1");
        assertThat(m.get("submissionId")).isEqualTo(222L);
        assertThat(m.get("completedAt")).isInstanceOf(Instant.class);
    }

    @Test
    void upsertCompleted_updates_when_existing_does_not_overwrite_submissionId_if_null() {
        QuestCompletion existing = QuestCompletion.builder()
                .questId(5L).userId("u1").submissionId(111L)
                .status(QuestStatus.ACTIVE)
                .completedAt(Instant.parse("2025-01-01T00:00:00Z"))
                .build();
        when(completions.findByQuestIdAndUserId(5L, "u1")).thenReturn(Optional.of(existing));

        QuestCompletion out = service.upsertCompleted(5L, "u1", null);

        assertThat(out.getStatus()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(out.getSubmissionId()).isEqualTo(111L);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TOPIC), eq("5"), eq("QuestCompleted"), eq(1), eq("quest-service"), cap.capture());
        Map<String, Object> m = cap.getValue();
        assertThat(m.get("submissionId")).isEqualTo(111L);
    }

    @Test
    void upsertCompleted_creates_with_null_submissionId_allows_and_emits() {
        when(completions.findByQuestIdAndUserId(7L, "u9")).thenReturn(Optional.empty());

        QuestCompletion saved = service.upsertCompleted(7L, "u9", null);

        assertThat(saved.getSubmissionId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(QuestStatus.COMPLETED);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(TOPIC), eq("7"), eq("QuestCompleted"), eq(1), eq("quest-service"), cap.capture());
        Map<String, Object> m = cap.getValue();
        assertThat(m.get("questId")).isEqualTo(7L);
        assertThat(m.get("userId")).isEqualTo("u9");
        assertThat(m.get("submissionId")).isNull();
    }

    /* ---------------------------- read-only helpers ---------------------------- */

    @Test
    void isCompleted_true_false() {
        when(completions.existsByQuestIdAndUserId(1L, "u1")).thenReturn(true);
        when(completions.existsByQuestIdAndUserId(2L, "u1")).thenReturn(false);
        assertTrue(service.isCompleted(1L, "u1"));
        assertFalse(service.isCompleted(2L, "u1"));
    }

    @Test
    void countForQuest_passthrough() {
        when(completions.countByQuestId(99L)).thenReturn(5L);
        assertThat(service.countForQuest(99L)).isEqualTo(5L);
    }
}
