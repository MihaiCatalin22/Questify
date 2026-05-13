package com.questify.mapper;

import com.questify.domain.Quest;
import com.questify.domain.QuestParticipant;
import com.questify.dto.ParticipantResponse;
import com.questify.dto.QuestDtos;

import java.util.List;
import java.util.Set;

public class QuestMapper {
    public static QuestDtos.QuestRes toRes(Quest q, int participantsCount, boolean completedByCurrentUser) {
        QuestDtos.VerificationPolicyDto verificationPolicy = new QuestDtos.VerificationPolicyDto(
                sortedSignals(q.getVerificationRequiredEvidence()),
                sortedSignals(q.getVerificationOptionalEvidence()),
                sortedSignals(q.getVerificationDisqualifiers()),
                q.getVerificationMinSupportScore(),
                q.getVerificationTaskType()
        );
        return new QuestDtos.QuestRes(
                q.getId(), q.getTitle(), q.getDescription(),
                q.getCategory(), q.getStatus(),
                q.getStartDate(), q.getEndDate(),
                q.getCreatedAt(), q.getUpdatedAt(),
                q.getCreatedByUserId(),
                participantsCount,
                completedByCurrentUser,
                q.getVisibility(),
                verificationPolicy
        );
    }

    public static List<ParticipantResponse> toParticipantDtos(List<QuestParticipant> entities) {
        return entities.stream()
                .map(p -> new ParticipantResponse(p.getId(), p.getUserId(), p.getJoinedAt()))
                .toList();
    }

    private static List<String> sortedSignals(Set<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .sorted(String::compareToIgnoreCase)
                .toList();
    }
}
