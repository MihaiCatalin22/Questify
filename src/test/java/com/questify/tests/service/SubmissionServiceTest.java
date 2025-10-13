package com.questify.tests.service;

import com.questify.config.NotFoundException;
import com.questify.domain.Quest;
import com.questify.domain.Submission;
import com.questify.domain.User;
import com.questify.domain.ReviewStatus;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewSubmissionReq;
import com.questify.dto.SubmissionDtos.SubmissionRes;
import com.questify.mapper.SubmissionMapper;
import com.questify.persistence.*;
import com.questify.service.SubmissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SubmissionServiceTest {

    @Mock SubmissionRepository submissions;
    @Mock QuestRepository quests;
    @Mock UserRepository users;
    @Mock SubmissionMapper mapper;
    @InjectMocks SubmissionService service;

    @Test
    void create_ok_setsPending_and_maps() {
        CreateSubmissionReq req = new CreateSubmissionReq(11L, 22L, "done", null);

        Quest quest = new Quest(); quest.setId(11L);
        User user  = new User();   user.setId(22L);

        Submission mapped = new Submission();
        mapped.setProofText("done");

        Submission saved = new Submission();
        saved.setId(99L);
        saved.setQuest(quest);
        saved.setUser(user);
        saved.setProofText("done");
        saved.setReviewStatus(ReviewStatus.PENDING);
        saved.setCreatedAt(Instant.now());

        SubmissionRes res = new SubmissionRes(
                99L, 11L, 22L, "done", null, ReviewStatus.PENDING, null, saved.getCreatedAt());

        when(quests.findById(11L)).thenReturn(Optional.of(quest));
        when(users.findById(22L)).thenReturn(Optional.of(user));
        when(mapper.toEntity(req)).thenReturn(mapped);
        when(submissions.save(any(Submission.class))).thenReturn(saved);
        when(mapper.toRes(saved)).thenReturn(res);

        SubmissionRes out = service.create(req);

        assertThat(out.id()).isEqualTo(99L);
        verify(submissions).save(argThat(s ->
                s.getQuest()==quest && s.getUser()==user && s.getReviewStatus()==ReviewStatus.PENDING));
    }

    @Test
    void create_requiresTextOrUrl() {
        CreateSubmissionReq bad = new CreateSubmissionReq(1L, 2L, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(bad));
        verifyNoInteractions(quests, users, mapper, submissions);
    }

    @Test
    void create_missingQuestOrUser_throwsNotFound() {
        when(quests.findById(1L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.create(new CreateSubmissionReq(1L, 2L, "x", null)));

        when(quests.findById(1L)).thenReturn(Optional.of(new Quest() {{ setId(1L); }}));
        when(users.findById(2L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.create(new CreateSubmissionReq(1L, 2L, "x", null)));
    }

    @Test
    void getOrThrow_missing_404() {
        when(submissions.findById(77L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getOrThrow(77L));
    }

    @Test
    void listForQuest_all_or_byStatus() {
        Quest q = new Quest(); q.setId(3L);
        when(quests.findById(3L)).thenReturn(Optional.of(q));
        when(submissions.findByQuest(eq(q), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var all = service.listForQuest(3L, null, PageRequest.of(0, 5));
        assertThat(all.getContent()).isEmpty();

        when(submissions.findByQuestAndReviewStatus(eq(q), eq(ReviewStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var filtered = service.listForQuest(3L, ReviewStatus.PENDING, PageRequest.of(0, 5));
        assertThat(filtered.getContent()).isEmpty();
    }

    @Test
    void listForUser_ok() {
        User u = new User(); u.setId(4L);
        when(users.findById(4L)).thenReturn(Optional.of(u));
        when(submissions.findByUser(eq(u), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        var page = service.listForUser(4L, PageRequest.of(0, 5));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void review_onlyPending_allowed_rejectNeedsNote() {
        Submission pending = new Submission();
        pending.setId(10L);
        pending.setReviewStatus(ReviewStatus.PENDING);

        when(submissions.findById(10L)).thenReturn(Optional.of(pending));

        // approve ok
        ReviewSubmissionReq ok = new ReviewSubmissionReq(ReviewStatus.APPROVED, "looks good");
        service.review(10L, ok);
        assertThat(pending.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);

        // set to pending again and reject without note -> 400
        pending.setReviewStatus(ReviewStatus.PENDING);
        ReviewSubmissionReq bad = new ReviewSubmissionReq(ReviewStatus.REJECTED, "  ");
        assertThrows(IllegalArgumentException.class, () -> service.review(10L, bad));

        // already approved -> cannot re-review
        pending.setReviewStatus(ReviewStatus.APPROVED);
        ReviewSubmissionReq again = new ReviewSubmissionReq(ReviewStatus.REJECTED, "dup");
        assertThrows(IllegalArgumentException.class, () -> service.review(10L, again));
    }
}
