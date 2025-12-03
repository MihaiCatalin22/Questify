package com.questify.mapper;

import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos.SubmissionRes;

public class SubmissionMapper {
    public static SubmissionRes toRes(Submission s) {
        return new SubmissionRes(
                s.getId(), s.getQuestId(), s.getUserId(), s.getProofKey(), s.getNote(),
                s.getStatus(), s.getReviewerUserId(), s.getReviewedAt(), s.getCreatedAt(), s.getUpdatedAt()
        );
    }
}
