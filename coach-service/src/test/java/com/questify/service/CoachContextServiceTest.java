package com.questify.service;

import com.questify.client.QuestCoachContextClient;
import com.questify.client.UserCoachSettingsClient;
import com.questify.dto.CoachDtos.QuestCoachContextRes;
import com.questify.dto.CoachDtos.RecentCompletionRes;
import com.questify.dto.CoachDtos.UserCoachSettingsRes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachContextServiceTest {

    @Mock UserCoachSettingsClient userCoachSettingsClient;
    @Mock QuestCoachContextClient questCoachContextClient;

    @InjectMocks CoachContextService service;

    @Test
    void loadContext_uses_explicit_goal_and_limits_prompt_data() {
        when(userCoachSettingsClient.getCoachSettings("u1"))
                .thenReturn(new UserCoachSettingsRes(true, "Walk daily"));
        when(questCoachContextClient.getCoachContext("u1", true))
                .thenReturn(new QuestCoachContextRes(
                        List.of(" Evening walk ", "", "Stretch", "Hydrate", "Journal", "Read"),
                        List.of(
                                new RecentCompletionRes(" Morning run ", Instant.parse("2026-03-01T08:00:00Z")),
                                new RecentCompletionRes("", Instant.parse("2026-02-28T10:00:00Z"))
                        ),
                        6,
                        12
                ));

        var context = service.loadContext("u1", true);

        assertThat(context.goal()).isEqualTo("Walk daily");
        assertThat(context.activeQuestTitles()).containsExactly("Evening walk", "Stretch", "Hydrate", "Journal", "Read");
        assertThat(context.recentCompletions()).hasSize(1);
        assertThat(context.recentCompletions().get(0).title()).isEqualTo("Morning run");
        assertThat(context.activeQuestCount()).isEqualTo(6);
        assertThat(context.totalCompletedCount()).isEqualTo(12);
    }

    @Test
    void loadContext_builds_goal_from_active_titles_when_goal_missing() {
        when(userCoachSettingsClient.getCoachSettings("u1"))
                .thenReturn(new UserCoachSettingsRes(true, "   "));
        when(questCoachContextClient.getCoachContext("u1", false))
                .thenReturn(new QuestCoachContextRes(
                        List.of("Evening walk", "Stretch"),
                        List.of(new RecentCompletionRes("Morning run", Instant.parse("2026-03-01T08:00:00Z"))),
                        2,
                        7
                ));

        var context = service.loadContext("u1", false);

        assertThat(context.goal()).isEqualTo("Stay consistent with: Evening walk, Stretch");
        assertThat(context.recentCompletions()).isEmpty();
    }

    @Test
    void loadContext_rejects_when_opt_in_disabled() {
        when(userCoachSettingsClient.getCoachSettings("u1"))
                .thenReturn(new UserCoachSettingsRes(false, "Walk daily"));

        assertThatThrownBy(() -> service.loadContext("u1", true))
                .isInstanceOf(AiCoachOptInRequiredException.class)
                .hasMessage("AI Coach opt-in is not enabled");

        verify(questCoachContextClient, never()).getCoachContext("u1", true);
    }
}
