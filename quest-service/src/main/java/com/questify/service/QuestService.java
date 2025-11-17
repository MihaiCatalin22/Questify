package com.questify.service;

import com.questify.domain.*;
import com.questify.dto.QuestDtos.*;
import com.questify.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestService {
    private final QuestRepository quests;
    private final QuestParticipantRepository participants;

    public QuestService(QuestRepository quests, QuestParticipantRepository participants) {
        this.quests = quests;
        this.participants = participants;
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
        return quests.save(q);
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
        return quests.save(q);
    }

    @Transactional
    public Quest updateStatus(Long id, UpdateQuestStatusReq req, String requesterUserId) {
        var q = get(id);
        if (!q.getCreatedByUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("Only owner can update status.");
        }
        q.setStatus(req.status());
        return quests.save(q);
    }

    @Transactional
    public Quest archive(Long id, String requesterUserId) {
        var q = get(id);
        if (!q.getCreatedByUserId().equals(requesterUserId)) {
            throw new AccessDeniedException("Only owner can archive.");
        }
        q.setStatus(QuestStatus.ARCHIVED);
        return quests.save(q);
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
        // idempotent
        if (participants.findByQuest_IdAndUserId(questId, userId).isPresent()) return;
        try {
            participants.save(new QuestParticipant(q, userId));
        } catch (DataIntegrityViolationException ignored) {
            // unique constraint race -> treated as joined
        }
    }

    @Transactional
    public void leave(Long questId, String userId) {
        participants.findByQuest_IdAndUserId(questId, userId).ifPresent(participants::delete);
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
