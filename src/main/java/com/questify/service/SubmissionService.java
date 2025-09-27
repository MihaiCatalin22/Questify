package com.questify.service;

import com.questify.config.NotFoundException;
import com.questify.domain.*;
import com.questify.dto.SubmissionDtos;
import com.questify.mapper.SubmissionMapper;
import com.questify.persistence.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service
@Transactional
public class SubmissionService {
    private final SubmissionRepository submissions;
    private final QuestRepository quests;
    private final UserRepository users;
    private final SubmissionMapper mapper;


    public SubmissionService(SubmissionRepository submissions, QuestRepository quests, UserRepository users, SubmissionMapper mapper) {
        this.submissions = submissions; this.quests = quests; this.users = users; this.mapper = mapper; }


    public SubmissionDtos.SubmissionRes create(@Valid SubmissionDtos.CreateSubmissionReq req) {
        if (!StringUtils.hasText(req.proofText()) && !StringUtils.hasText(req.proofUrl())) {
            throw new IllegalArgumentException("Provide proofText or proofUrl");
        }
        Quest q = quests.findById(req.questId()).orElseThrow(() -> new NotFoundException("Quest not found: " + req.questId()));
        User u = users.findById(req.userId()).orElseThrow(() -> new NotFoundException("User not found: " + req.userId()));


        Submission s = mapper.toEntity(req);
        s.setQuest(q); s.setUser(u); s.setReviewStatus(ReviewStatus.PENDING);
        return mapper.toRes(submissions.save(s));
    }


    @Transactional(readOnly = true)
    public SubmissionDtos.SubmissionRes getOrThrow(Long id) {
        return mapper.toRes(submissions.findById(id).orElseThrow(() -> new NotFoundException("Submission not found: " + id)));
    }


    @Transactional(readOnly = true)
    public Page<SubmissionDtos.SubmissionRes> listForQuest(Long questId, ReviewStatus status, Pageable pageable) {
        Quest q = quests.findById(questId).orElseThrow(() -> new NotFoundException("Quest not found: " + questId));
        Page<Submission> page = (status == null)
                ? submissions.findByQuest(q, pageable)
                : submissions.findByQuestAndReviewStatus(q, status, pageable);
        return page.map(mapper::toRes);
    }


    @Transactional(readOnly = true)
    public Page<SubmissionDtos.SubmissionRes> listForUser(Long userId, Pageable pageable) {
        User u = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
        return submissions.findByUser(u, pageable).map(mapper::toRes);
    }


    public SubmissionDtos.SubmissionRes review(Long submissionId, @Valid SubmissionDtos.ReviewSubmissionReq req) {
        Submission s = submissions.findById(submissionId).orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));
        if (s.getReviewStatus() != ReviewStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING submissions can be reviewed");
        }
        if (req.reviewStatus() == ReviewStatus.REJECTED && !StringUtils.hasText(req.reviewNote())) {
            throw new IllegalArgumentException("Review note is required when rejecting");
        }
        s.setReviewStatus(req.reviewStatus());
        s.setReviewNote(req.reviewNote());
        return mapper.toRes(s);
    }

}
