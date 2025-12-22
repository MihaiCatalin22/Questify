package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestAccessClient;
import com.questify.client.QuestProgressClient;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.SubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock SubmissionRepository submissions;
    @Mock QuestAccessClient questAccess;
    @Mock ProofClient proofClient;
    @Mock QuestProgressClient questProgress;
    @Mock EventPublisher events;

    @InjectMocks SubmissionService service;

    private static final String SUBMISSIONS_TOPIC = "dev.questify.submissions";
    private static final String PROOFS_TOPIC = "dev.questify.proofs";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "submissionsTopic", SUBMISSIONS_TOPIC);
        ReflectionTestUtils.setField(service, "proofsTopic", PROOFS_TOPIC);

        // auto-assign IDs when saving
        when(submissions.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            if (s.getId() == null) s.setId(1L);
            return s;
        });
    }

    private Submission sub(long id, long questId, String userId, ReviewStatus status) {
        Submission s = new Submission();
        s.setId(id);
        s.setQuestId(questId);
        s.setUserId(userId);
        s.setStatus(status);
        s.setCreatedAt(Instant.parse("2025-01-01T00:00:00Z"));
        return s;
    }

    /* ---------------------------- create ---------------------------- */

    @Test
    void create_denied_if_not_allowed() {
        CreateSubmissionReq req = new CreateSubmissionReq(7L, "proof/7", "hi");
        when(questAccess.allowed("u1", 7L)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.create("u1", req));

        verifyNoInteractions(events);
        verify(submissions, never()).save(any());
    }

//    @Test
//    void create_ok_saves_and_emits_including_optional_fields_when_present() {
//        CreateSubmissionReq req = new CreateSubmissionReq(7L, "proof/7", "hello-note");
//        when(questAccess.allowed("u1", 7L)).thenReturn(true);
//
//        when(submissions.save(any())).thenAnswer(inv -> {
//            Submission s = inv.getArgument(0);
//            s.setId(77L);
//            return s;
//        });
//
//        Submission saved = service.create("u1", req);
//
//        assertThat(saved.getId()).isEqualTo(77L);
//        assertThat(saved.getQuestId()).isEqualTo(7L);
//        assertThat(saved.getUserId()).isEqualTo("u1");
//        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.PENDING);
//
//        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
//        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("7"),
//                eq("SubmissionCreated"), eq(1), eq("submission-service"), cap.capture());
//
//        Map<String, Object> payload = cap.getValue();
//        assertThat(payload).containsEntry("submissionId", 77L);
//        assertThat(payload).containsEntry("questId", 7L);
//        assertThat(payload).containsEntry("userId", "u1");
//        assertThat(payload).containsEntry("status", "PENDING");
//        assertThat(payload).containsEntry("note", "hello-note");
//        assertThat(payload).containsEntry("proofKey", "proof/7");
//    }

    @Test
    void create_ok_excludes_blank_optional_fields_from_payload() {
        CreateSubmissionReq req = new CreateSubmissionReq(9L, "  ", "   ");
        when(questAccess.allowed("uX", 9L)).thenReturn(true);

        when(submissions.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(901L);
            return s;
        });

        Submission saved = service.create("uX", req);
        assertThat(saved.getId()).isEqualTo(901L);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("9"),
                eq("SubmissionCreated"), eq(1), eq("submission-service"), cap.capture());

        Map<String, Object> payload = cap.getValue();
        assertThat(payload).containsKeys("submissionId", "questId", "userId", "status");
        assertThat(payload).doesNotContainKeys("note", "proofKey"); // excluded when blank
    }

    /* ---------------------------- createFromMultipart (error paths we can assert without return DTO type) ---------------------------- */

    @Test
    void createFromMultipart_denied_if_not_allowed() {
        when(questAccess.allowed("u1", 5L)).thenReturn(false);
        MultipartFile file = mock(MultipartFile.class);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.createFromMultipart(5L, "n", file, "u1", "Bearer x"));

        verifyNoInteractions(events);
        verifyNoInteractions(proofClient);
        verify(submissions, never()).save(any());
    }

    @Test
    void createFromMultipart_propagates_ResponseStatusException_from_upload() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);
        MultipartFile file = mock(MultipartFile.class);
        when(proofClient.upload(any(), any())).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "bad upload"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipart(5L, "note", file, "u1", "Bearer t"));
        assertThat(ex.getStatusCode().value()).isEqualTo(400);

        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void createFromMultipart_wraps_unexpected_exception_as_500() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);
        MultipartFile file = mock(MultipartFile.class);
        when(proofClient.upload(any(), any())).thenThrow(new RuntimeException("boom"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipart(5L, "note", file, "u1", "Bearer t"));
        assertThat(ex.getStatusCode().value()).isEqualTo(500);

        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
    }

    /* ---------------------------- get ---------------------------- */

    @Test
    void get_found() {
        Submission s = sub(2L, 10L, "u2", ReviewStatus.PENDING);
        when(submissions.findById(2L)).thenReturn(Optional.of(s));

        Submission out = service.get(2L);
        assertThat(out).isSameAs(s);
    }

    @Test
    void get_404() {
        when(submissions.findById(404L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.get(404L));
    }

    /* ---------------------------- listing helpers ---------------------------- */

    @Test
    void mine_passthrough_and_sorted() {
        Submission s = sub(1L, 5L, "me", ReviewStatus.APPROVED);
        when(submissions.findByUserIdOrderByCreatedAtDesc(eq("me"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(s)));

        Page<Submission> page = service.mine("me", 0, 10);
        assertThat(page.getContent()).hasSize(1);
        verify(submissions).findByUserIdOrderByCreatedAtDesc(eq("me"), any(PageRequest.class));
    }

    @Test
    void forQuest_passthrough_and_sorted() {
        Submission s = sub(1L, 7L, "x", ReviewStatus.PENDING);
        when(submissions.findByQuestIdOrderByCreatedAtDesc(eq(7L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(s)));

        Page<Submission> page = service.forQuest(7L, 1, 5);
        assertThat(page.getContent()).hasSize(1);
        verify(submissions).findByQuestIdOrderByCreatedAtDesc(eq(7L), any(PageRequest.class));
    }

    @Test
    void pending_passthrough() {
        when(submissions.findByStatus(eq(ReviewStatus.PENDING), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.pending(0, 20).getContent()).isEmpty();
    }

    @Test
    void byStatus_passthrough() {
        when(submissions.findByStatus(eq(ReviewStatus.REJECTED), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.byStatus(ReviewStatus.REJECTED, 0, 20).getContent()).isEmpty();
    }

    /* ---------------------------- review ---------------------------- */

    @Test
    void review_sets_fields_saves_emits_and_calls_markCompleted_on_approved() {
        Submission existing = sub(33L, 9L, "u9", ReviewStatus.PENDING);
        existing.setNote("old");
        when(submissions.findById(33L)).thenReturn(Optional.of(existing));

        ReviewReq req = new ReviewReq(ReviewStatus.APPROVED, " well done ");
        when(submissions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Submission out = service.review(33L, req, "rev1");

        assertThat(out.getStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(out.getReviewerUserId()).isEqualTo("rev1");
        assertNotNull(out.getReviewedAt());
        // note gets overwritten (non-blank)
        assertThat(out.getNote()).isEqualTo(" well done ");

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("9"),
                eq("SubmissionReviewed"), eq(1), eq("submission-service"), cap.capture());

        Map<String, Object> payload = cap.getValue();
        assertThat(payload).containsEntry("submissionId", 33L);
        assertThat(payload).containsEntry("questId", 9L);
        assertThat(payload).containsEntry("userId", "u9");
        assertThat(payload).containsEntry("reviewStatus", "APPROVED");
        assertThat(payload).containsEntry("reviewerId", "rev1");

        verify(questProgress).markCompleted(9L, "u9", 33L);
    }

    @Test
    void review_rejected_does_not_call_markCompleted_and_note_not_overwritten_when_blank() {
        Submission existing = sub(44L, 12L, "u12", ReviewStatus.PENDING);
        existing.setNote("keep-me");
        when(submissions.findById(44L)).thenReturn(Optional.of(existing));

        ReviewReq req = new ReviewReq(ReviewStatus.REJECTED, "  "); // blank -> do not overwrite
        when(submissions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Submission out = service.review(44L, req, "rev2");

        assertThat(out.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(out.getNote()).isEqualTo("keep-me"); // preserved

        verify(questProgress, never()).markCompleted(anyLong(), anyString(), anyLong());

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("12"),
                eq("SubmissionReviewed"), eq(1), eq("submission-service"), cap.capture());

        Map<String, Object> payload = cap.getValue();
        assertThat(payload).containsEntry("reviewStatus", "REJECTED");
    }

    /* ---------------------------- proof URLs passthrough ---------------------------- */

    @Test
    void signedGetUrl_passthrough() {
        when(proofClient.signGet("k")).thenReturn("SIGNED");
        assertThat(service.signedGetUrl("k")).isEqualTo("SIGNED");
    }

    @Test
    void publicUrl_passthrough() {
        when(proofClient.publicUrl("k")).thenReturn("PUB");
        assertThat(service.publicUrl("k")).isEqualTo("PUB");
    }
}
