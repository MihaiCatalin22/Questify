package com.questify.service;

import com.questify.domain.*;
import com.questify.dto.QuestDtos.*;
import com.questify.kafka.EventPublisher;
import com.questify.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class QuestService {
    private final QuestRepository quests;
    private final QuestParticipantRepository participants;
    private final EventPublisher events;

    @Value("${app.kafka.topics.quests:quests}")
    private String questsTopic;

    public QuestService(QuestRepository quests, QuestParticipantRepository participants, EventPublisher events) {
        this.quests = quests;
        this.participants = participants;
        this.events = events;
    }

    @Transactional
    public Quest create(CreateQuestReq req, String requesterUserId) {
        if (requesterUserId == null || !requesterUserId.equals(req.createdByUserId())) {
            throw new AccessDeniedException("You can only create quests for yourself.");
        }
        var q = Quest.builder()
                .title(req.title())
                .description(req.description())
                .category(req.category())
                .status(QuestStatus.ACTIVE)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .visibility(req.visibility())
                .createdByUserId(req.createdByUserId())
                .build();
        var saved = quests.save(q);

        events.publish(
                questsTopic,
                String.valueOf(saved.getId()),
                "QuestCreated", 1, "quest-service",
                Map.of(
                        "questId", saved.getId(),
                        "createdByUserId", saved.getCreatedByUserId(),
                        "title", saved.getTitle(),
                        "description", saved.getDescription(),
                        "category", saved.getCategory().name(),
                        "visibility", saved.getVisibility().name(),
                        "status", saved.getStatus().name(),
                        "startDate", saved.getStartDate(),
                        "endDate", saved.getEndDate()
                )
        );

        return saved;
    }

    public Quest get(Long id) {
        return quests.findById(id).orElseThrow(() -> new EntityNotFoundException("Quest %d not found".formatted(id)));
    }

    @Transactional
    public Quest update(Long id, UpdateQuestReq req, String requesterUserId) {
        var q = get(id);
        if (!q.getCreatedByUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("Only owner can edit.");
        }
        q.setTitle(req.title());
        q.setDescription(req.description());
        q.setCategory(req.category());
        q.setStartDate(req.startDate());
        q.setEndDate(req.endDate());
        q.setVisibility(req.visibility());
        var saved = quests.save(q);

        events.publish(
                questsTopic,
                String.valueOf(saved.getId()),
                "QuestUpdated", 1, "quest-service",
                Map.of(
                        "questId", saved.getId(),
                        "title", saved.getTitle(),
                        "description", saved.getDescription(),
                        "category", saved.getCategory().name(),
                        "visibility", saved.getVisibility().name(),
                        "startDate", saved.getStartDate(),
                        "endDate", saved.getEndDate()
                )
        );

        return saved;
    }

    @Transactional
    public Quest updateStatus(Long id, UpdateQuestStatusReq req, String requesterUserId) {
        var q = get(id);
        if (!q.getCreatedByUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("Only owner can update status.");
        }
        q.setStatus(req.status());
        var saved = quests.save(q);

        events.publish(
                questsTopic,
                String.valueOf(saved.getId()),
                "QuestStatusUpdated", 1, "quest-service",
                Map.of(
                        "questId", saved.getId(),
                        "status", saved.getStatus().name()
                )
        );

        return saved;
    }

    @Transactional
    public Quest archive(Long id, String requesterUserId) {
        var q = get(id);
        if (!q.getCreatedByUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("Only owner can archive.");
        }
        q.setStatus(QuestStatus.ARCHIVED);
        var saved = quests.save(q);

        events.publish(
                questsTopic,
                String.valueOf(saved.getId()),
                "QuestArchived", 1, "quest-service",
                Map.of(
                        "questId", saved.getId(),
                        "status", saved.getStatus().name()
                )
        );

        return saved;
    }

    public Page<Quest> listByStatus(QuestStatus status, Pageable pageable) {
        return quests.findByStatus(status, pageable);
    }

    public Page<Quest> mine(String userId, Pageable pageable) {
        return quests.findByCreatedByUserId(userId, pageable);
    }

    public Page<Quest> mineOrParticipating(String userId, Pageable pageable) {
        return quests.findMyOrParticipating(userId, pageable);
    }

    public Page<Quest> discoverActive(Pageable pageable) {
        return quests.findByVisibilityAndStatus(QuestVisibility.PUBLIC, QuestStatus.ACTIVE, pageable);
    }

    public Page<Quest> searchPublic(String q, Pageable pageable) {
        return quests.searchPublic(q, pageable);
    }

    @Transactional
    public void join(Long questId, String userId) {
        var q = get(questId);
        if (q.getVisibility() != QuestVisibility.PUBLIC) {
            throw new AccessDeniedException("This quest is private.");
        }
        if (participants.findByQuest_IdAndUserId(questId, userId).isPresent()) return;
        try {
            participants.save(new QuestParticipant(q, userId));

            events.publish(
                    questsTopic,
                    String.valueOf(questId),
                    "ParticipantJoined", 1, "quest-service",
                    Map.of("questId", questId, "userId", userId)
            );
        } catch (DataIntegrityViolationException ignored) {
        }
    }

    @Transactional
    public void leave(Long questId, String userId) {
        var existing = participants.findByQuest_IdAndUserId(questId, userId);
        if (existing.isPresent()) {
            participants.delete(existing.get());

            events.publish(
                    questsTopic,
                    String.valueOf(questId),
                    "ParticipantLeft", 1, "quest-service",
                    Map.of("questId", questId, "userId", userId)
            );
        }
    }

    public Page<Quest> mineByStatus(String userId, QuestStatus status, Pageable pageable) {
        return quests.findByCreatedByUserIdAndStatus(userId, status, pageable);
    }

    public Page<Quest> mineOrParticipatingWithStatus(String userId, QuestStatus status, Pageable pageable) {
        return quests.findMyOrParticipatingWithStatus(userId, status, pageable);
    }

    public Page<Quest> mineOrParticipatingNotStatus(String userId, QuestStatus status, Pageable pageable) {
        return quests.findMyOrParticipatingNotStatus(userId, status, pageable);
    }

    public Page<Quest> mineOrParticipatingFiltered(String userId, Boolean archived, Pageable pageable) {
        if (archived == null) return quests.findMyOrParticipating(userId, pageable);
        return archived
                ? quests.findMyOrParticipatingWithStatus(userId, QuestStatus.ARCHIVED, pageable)
                : quests.findMyOrParticipatingNotStatus(userId, QuestStatus.ARCHIVED, pageable);
    }

    public int participantsCount(Long questId) {
        return Math.toIntExact(participants.countByQuest_Id(questId));
    }

    public boolean isOwnerOrParticipant(Long questId, String userId) {
        return quests.findById(questId).map(q ->
                userId.equals(q.getCreatedByUserId()) ||
                        participants.findByQuest_IdAndUserId(questId, userId).isPresent()
        ).orElse(false);
    }
}
