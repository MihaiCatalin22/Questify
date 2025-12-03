package com.questify.mapper;

import com.questify.domain.Quest;
import com.questify.domain.QuestParticipant;
import com.questify.dto.ParticipantResponse;
import com.questify.dto.QuestDtos;

import java.util.List;

public class QuestMapper {
    public static QuestDtos.QuestRes toRes(Quest q, int participantsCount, boolean completedByCurrentUser) {
        return new QuestDtos.QuestRes(
                q.getId(), q.getTitle(), q.getDescription(),
                q.getCategory(), q.getStatus(),
                q.getStartDate(), q.getEndDate(),
                q.getCreatedAt(), q.getUpdatedAt(),
                q.getCreatedByUserId(),
                participantsCount,
                completedByCurrentUser,
                q.getVisibility()
        );
    }

    public static List<ParticipantResponse> toParticipantDtos(List<QuestParticipant> entities) {
        return entities.stream()
                .map(p -> new ParticipantResponse(p.getId(), p.getUserId(), p.getJoinedAt()))
                .toList();
    }
}
