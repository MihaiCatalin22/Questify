package com.questify.service;

import com.questify.domain.QuestStatus;
import com.questify.dto.CoachContextDtos.CoachContextRes;
import com.questify.dto.CoachContextDtos.RecentCompletionRes;
import com.questify.repository.QuestCompletionRepository;
import com.questify.repository.QuestRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CoachContextService {

    private static final int ACTIVE_TITLE_LIMIT = 5;
    private static final int RECENT_COMPLETION_LIMIT = 5;

    private final QuestRepository quests;
    private final QuestCompletionRepository completions;

    public CoachContextService(QuestRepository quests, QuestCompletionRepository completions) {
        this.quests = quests;
        this.completions = completions;
    }

    @Transactional(readOnly = true)
    public CoachContextRes getCoachContext(String userId, boolean includeRecentHistory) {
        var activeTitles = quests.findMyOrParticipatingWithStatus(
                        userId,
                        QuestStatus.ACTIVE,
                        PageRequest.of(
                                0,
                                ACTIVE_TITLE_LIMIT,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
                        )
                )
                .stream()
                .map(q -> q.getTitle() == null ? "" : q.getTitle().trim())
                .filter(title -> !title.isBlank())
                .toList();

        var recentCompletions = includeRecentHistory
                ? completions.findRecentCoachCompletions(
                                userId,
                                PageRequest.of(0, RECENT_COMPLETION_LIMIT)
                        ).stream()
                        .map(c -> new RecentCompletionRes(c.getTitle(), c.getCompletedAt()))
                        .toList()
                : List.<RecentCompletionRes>of();

        return new CoachContextRes(
                activeTitles,
                recentCompletions,
                quests.countMyOrParticipatingWithStatus(userId, QuestStatus.ACTIVE),
                completions.countMyCompletedFiltered(userId, null)
        );
    }
}
