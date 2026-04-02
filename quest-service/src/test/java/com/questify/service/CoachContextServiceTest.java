package com.questify.service;

import com.questify.domain.Quest;
import com.questify.domain.QuestStatus;
import com.questify.domain.QuestVisibility;
import com.questify.repository.QuestCompletionRepository;
import com.questify.repository.QuestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachContextServiceTest {

    @Mock QuestRepository quests;
    @Mock QuestCompletionRepository completions;

    @InjectMocks CoachContextService service;

    @Test
    void getCoachContext_limits_to_minimal_fields_and_includes_recent_history() {
        var q1 = quest(1L, "Evening walk");
        var q2 = quest(2L, "Stretch for 10 minutes");
        when(quests.findMyOrParticipatingWithStatus(eq("u1"), eq(QuestStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of(q1, q2)));
        when(quests.countMyOrParticipatingWithStatus("u1", QuestStatus.ACTIVE)).thenReturn(2L);
        when(completions.countMyCompletedFiltered("u1", null)).thenReturn(7L);
        when(completions.findRecentCoachCompletions(eq("u1"), any()))
                .thenReturn(List.of(
                        completion("Morning run", Instant.parse("2026-03-01T08:00:00Z")),
                        completion("Drink water", Instant.parse("2026-02-28T10:00:00Z"))
                ));

        var res = service.getCoachContext("u1", true);

        assertThat(res.activeQuestTitles()).containsExactly("Evening walk", "Stretch for 10 minutes");
        assertThat(res.recentCompletions()).hasSize(2);
        assertThat(res.recentCompletions().get(0).title()).isEqualTo("Morning run");
        assertThat(res.activeQuestCount()).isEqualTo(2L);
        assertThat(res.totalCompletedCount()).isEqualTo(7L);
    }

    @Test
    void getCoachContext_omits_recent_history_when_disabled() {
        when(quests.findMyOrParticipatingWithStatus(eq("u1"), eq(QuestStatus.ACTIVE), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(quests.countMyOrParticipatingWithStatus("u1", QuestStatus.ACTIVE)).thenReturn(0L);
        when(completions.countMyCompletedFiltered("u1", null)).thenReturn(0L);

        var res = service.getCoachContext("u1", false);

        assertThat(res.recentCompletions()).isEmpty();
        verify(completions, never()).findRecentCoachCompletions(eq("u1"), any());
    }

    private static Quest quest(Long id, String title) {
        return Quest.builder()
                .id(id)
                .title(title)
                .description("Valid description for " + title)
                .status(QuestStatus.ACTIVE)
                .visibility(QuestVisibility.PUBLIC)
                .createdByUserId("u1")
                .startDate(Instant.parse("2026-01-01T00:00:00Z"))
                .endDate(Instant.parse("2026-12-31T00:00:00Z"))
                .build();
    }

    private static QuestCompletionRepository.RecentCoachCompletionProjection completion(String title, Instant completedAt) {
        return new QuestCompletionRepository.RecentCoachCompletionProjection() {
            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public Instant getCompletedAt() {
                return completedAt;
            }
        };
    }
}
