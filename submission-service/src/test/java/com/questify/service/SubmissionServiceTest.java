package com.questify.service;

import com.questify.client.ProofClient;
import com.questify.client.QuestAccessClient;
import com.questify.client.QuestProgressClient;
import com.questify.consistency.ProcessedEventService;
import com.questify.domain.ProofScanStatus;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.domain.SubmissionProof;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewReq;
import com.questify.kafka.EventPublisher;
import com.questify.repository.SubmissionProofRepository;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
class SubmissionServiceTest {

    @Mock SubmissionRepository submissions;
    @Mock SubmissionProofRepository submissionProofs;
    @Mock QuestAccessClient questAccess;
    @Mock ProofClient proofClient;
    @Mock QuestProgressClient questProgress;
    @Mock EventPublisher events;
    @Mock ProcessedEventService processedEvents;

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

        when(submissionProofs.save(any(SubmissionProof.class))).thenAnswer(inv -> inv.getArgument(0));
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

    @SuppressWarnings("unchecked")
    private <T> T uploadResWithKey(String key) {
        try {
            Method m = ProofClient.class.getMethod("upload", MultipartFile.class, String.class);
            Class<?> rt = m.getReturnType();

            // If it's an interface, proxy it.
            if (rt.isInterface()) {
                Object proxy = Proxy.newProxyInstance(
                        rt.getClassLoader(),
                        new Class<?>[]{rt},
                        (p, method, args) -> {
                            if ("key".equals(method.getName()) && method.getParameterCount() == 0) return key;
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == long.class) return 0L;
                            if (method.getReturnType() == double.class) return 0.0d;
                            return null;
                        }
                );
                return (T) proxy;
            }

            // If it's a record, instantiate via canonical constructor.
            if (rt.isRecord()) {
                var comps = rt.getRecordComponents();
                Class<?>[] types = Arrays.stream(comps).map(rc -> rc.getType()).toArray(Class[]::new);
                Object[] args = new Object[comps.length];

                for (int i = 0; i < comps.length; i++) {
                    String name = comps[i].getName();
                    Class<?> t = comps[i].getType();

                    if (t == String.class && "key".equalsIgnoreCase(name)) args[i] = key;
                    else if (t == String.class) args[i] = null;
                    else if (t == boolean.class) args[i] = false;
                    else if (t == int.class) args[i] = 0;
                    else if (t == long.class) args[i] = 0L;
                    else if (t == double.class) args[i] = 0.0d;
                    else args[i] = null;
                }

                Constructor<?> ctor = rt.getDeclaredConstructor(types);
                ctor.setAccessible(true);
                return (T) ctor.newInstance(args);
            }

            // Fallback: try single-String ctor
            try {
                Constructor<?> ctor = rt.getDeclaredConstructor(String.class);
                ctor.setAccessible(true);
                return (T) ctor.newInstance(key);
            } catch (NoSuchMethodException ignored) {
                // last resort: no good generic way; better to fail loudly
                throw new IllegalStateException("Cannot construct upload return type: " + rt.getName());
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        verify(submissionProofs, never()).save(any());
    }

    @Test
    void create_ok_saves_submission_scanning_saves_proof_and_emits_events_in_order() {
        CreateSubmissionReq req = new CreateSubmissionReq(7L, "proof/7", "hello-note");
        when(questAccess.allowed("u1", 7L)).thenReturn(true);

        when(submissions.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(77L);
            return s;
        });

        Submission saved = service.create("u1", req);

        assertThat(saved.getId()).isEqualTo(77L);
        assertThat(saved.getQuestId()).isEqualTo(7L);
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.SCANNING);
        assertThat(saved.getProofKey()).isEqualTo("proof/7");
        assertThat(saved.getNote()).isEqualTo("hello-note");

        ArgumentCaptor<SubmissionProof> proofCap = ArgumentCaptor.forClass(SubmissionProof.class);
        verify(submissionProofs).save(proofCap.capture());
        SubmissionProof proof = proofCap.getValue();
        assertThat(proof.getSubmissionId()).isEqualTo(77L);
        assertThat(proof.getProofKey()).isEqualTo("proof/7");
        assertThat(proof.getScanStatus()).isEqualTo(ProofScanStatus.PENDING);

        InOrder inOrder = inOrder(events);

        // ProofUploaded first
        ArgumentCaptor<Map<String, Object>> proofPayload = ArgumentCaptor.forClass(Map.class);
        inOrder.verify(events).publish(eq(PROOFS_TOPIC), eq("proof/7"),
                eq("ProofUploaded"), eq(1), eq("submission-service"), proofPayload.capture());
        assertThat(proofPayload.getValue()).containsEntry("submissionId", 77L);
        assertThat(proofPayload.getValue()).containsEntry("questId", 7L);
        assertThat(proofPayload.getValue()).containsEntry("userId", "u1");
        assertThat(proofPayload.getValue()).containsEntry("proofKey", "proof/7");

        // SubmissionCreated second
        ArgumentCaptor<Map<String, Object>> subPayload = ArgumentCaptor.forClass(Map.class);
        inOrder.verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("7"),
                eq("SubmissionCreated"), eq(1), eq("submission-service"), subPayload.capture());

        Map<String, Object> payload = subPayload.getValue();
        assertThat(payload).containsEntry("submissionId", 77L);
        assertThat(payload).containsEntry("questId", 7L);
        assertThat(payload).containsEntry("userId", "u1");
        assertThat(payload).containsEntry("status", "SCANNING");
        assertThat(payload).containsEntry("note", "hello-note");
        assertThat(payload).containsEntry("proofKey", "proof/7");
    }

    @Test
    void create_ok_excludes_blank_optional_fields_from_submissionCreated_payload() {
        CreateSubmissionReq req = new CreateSubmissionReq(9L, "proof/9", "   "); // blank note
        when(questAccess.allowed("uX", 9L)).thenReturn(true);

        when(submissions.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(901L);
            return s;
        });

        service.create("uX", req);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("9"),
                eq("SubmissionCreated"), eq(1), eq("submission-service"), cap.capture());

        Map<String, Object> payload = cap.getValue();
        assertThat(payload).containsKeys("submissionId", "questId", "userId", "status", "proofKey");
        assertThat(payload).doesNotContainKey("note");
    }

    /* ---------------------------- createFromMultipart / createFromMultipartMany ---------------------------- */

    @Test
    void createFromMultipart_denied_if_not_allowed() {
        when(questAccess.allowed("u1", 5L)).thenReturn(false);
        MultipartFile file = mock(MultipartFile.class);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.createFromMultipart(5L, "n", file, "u1", "Bearer x"));

        verifyNoInteractions(events);
        verifyNoInteractions(proofClient);
        verify(submissions, never()).save(any());
        verify(submissionProofs, never()).save(any());
    }

    @Test
    void createFromMultipartMany_400_if_files_null_or_all_empty() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);

        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipartMany(5L, "n", null, "u1", "Bearer x"));
        assertThat(ex1.getStatusCode().value()).isEqualTo(400);

        MultipartFile empty = mock(MultipartFile.class);
        when(empty.isEmpty()).thenReturn(true);

        List<MultipartFile> files = new ArrayList<>();
        files.add(null);
        files.add(empty);

        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipartMany(5L, "n", files, "u1", "Bearer x"));
        assertThat(ex2.getStatusCode().value()).isEqualTo(400);

        verifyNoInteractions(proofClient);
        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
    }


    @Test
    void createFromMultipartMany_400_if_more_than_10_files_after_filtering() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);

        List<MultipartFile> files = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            MultipartFile f = mock(MultipartFile.class);
            when(f.isEmpty()).thenReturn(false);
            files.add(f);
        }

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipartMany(5L, "n", files, "u1", "Bearer x"));
        assertThat(ex.getStatusCode().value()).isEqualTo(400);

        verifyNoInteractions(proofClient);
        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void createFromMultipart_propagates_ResponseStatusException_from_upload() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        when(proofClient.upload(any(), any())).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "bad upload"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipart(5L, "note", file, "u1", "Bearer t"));
        assertThat(ex.getStatusCode().value()).isEqualTo(400);

        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
        verify(proofClient, never()).deleteInternalObject(anyString());
    }

    @Test
    void createFromMultipart_wraps_unexpected_exception_as_500() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);

        when(proofClient.upload(any(), any())).thenThrow(new RuntimeException("boom"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipart(5L, "note", file, "u1", "Bearer t"));
        assertThat(ex.getStatusCode().value()).isEqualTo(500);

        verify(submissions, never()).save(any());
        verifyNoInteractions(events);
        verify(proofClient, never()).deleteInternalObject(anyString());
    }

    @Test
    void createFromMultipartMany_ok_uploads_all_creates_proofs_and_emits_events() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);

        MultipartFile f1 = mock(MultipartFile.class);
        MultipartFile f2 = mock(MultipartFile.class);
        MultipartFile f3 = mock(MultipartFile.class);
        when(f1.isEmpty()).thenReturn(false);
        when(f2.isEmpty()).thenReturn(false);
        when(f3.isEmpty()).thenReturn(false);

        when(submissions.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        // Return upload results with keys (type-agnostic helper)
        when(proofClient.upload(any(MultipartFile.class), eq("Bearer t")))
                .thenAnswer(inv -> uploadResWithKey("k1"))
                .thenAnswer(inv -> uploadResWithKey("k2"))
                .thenAnswer(inv -> uploadResWithKey("k3"));

        Submission saved = service.createFromMultipartMany(5L, "note", List.of(f1, f2, f3), "u1", "Bearer t");

        assertThat(saved.getId()).isEqualTo(100L);
        assertThat(saved.getQuestId()).isEqualTo(5L);
        assertThat(saved.getUserId()).isEqualTo("u1");
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.SCANNING);
        assertThat(saved.getProofKey()).isEqualTo("k1");

        verify(proofClient, times(3)).upload(any(MultipartFile.class), eq("Bearer t"));
        verify(submissionProofs, times(3)).save(any(SubmissionProof.class));

        // ProofUploaded 3x + SubmissionCreated 1x
        verify(events, times(4)).publish(anyString(), anyString(), anyString(), anyInt(), anyString(), anyMap());
        verify(events, times(3)).publish(eq(PROOFS_TOPIC), anyString(), eq("ProofUploaded"), eq(1), eq("submission-service"), anyMap());
        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("5"), eq("SubmissionCreated"), eq(1), eq("submission-service"), anyMap());
    }

    @Test
    void createFromMultipartMany_upload_fails_midway_cleans_up_successful_keys_and_does_not_emit_SubmissionCreated() {
        when(questAccess.allowed("u1", 5L)).thenReturn(true);

        MultipartFile f1 = mock(MultipartFile.class);
        MultipartFile f2 = mock(MultipartFile.class);
        when(f1.isEmpty()).thenReturn(false);
        when(f2.isEmpty()).thenReturn(false);

        when(submissions.save(any())).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(200L);
            return s;
        });

        when(proofClient.upload(any(MultipartFile.class), eq("Bearer t")))
                .thenAnswer(inv -> uploadResWithKey("k1"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "nope"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.createFromMultipartMany(5L, "note", List.of(f1, f2), "u1", "Bearer t"));
        assertThat(ex.getStatusCode().value()).isEqualTo(400);

        verify(proofClient).deleteInternalObject("k1");
        verify(events, never()).publish(eq(SUBMISSIONS_TOPIC), eq("5"), eq("SubmissionCreated"), anyInt(), anyString(), anyMap());
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

    @Test
    void all_passthrough_sorted() {
        when(submissions.findAllByOrderByCreatedAtDesc(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        assertThat(service.all(0, 20).getContent()).isEmpty();
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
        assertThat(out.getNote()).isEqualTo("keep-me");

        verify(questProgress, never()).markCompleted(anyLong(), anyString(), anyLong());

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(events).publish(eq(SUBMISSIONS_TOPIC), eq("12"),
                eq("SubmissionReviewed"), eq(1), eq("submission-service"), cap.capture());

        Map<String, Object> payload = cap.getValue();
        assertThat(payload).containsEntry("reviewStatus", "REJECTED");
    }

    @Test
    void review_409_if_trying_to_approve_while_still_scanning() {
        Submission existing = sub(55L, 99L, "u99", ReviewStatus.SCANNING);
        when(submissions.findById(55L)).thenReturn(Optional.of(existing));

        ReviewReq req = new ReviewReq(ReviewStatus.APPROVED, "ok");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.review(55L, req, "revX"));

        assertThat(ex.getStatusCode().value()).isEqualTo(409);

        verify(submissions, never()).save(any());
        verify(events, never()).publish(eq(SUBMISSIONS_TOPIC), anyString(), eq("SubmissionReviewed"), anyInt(), anyString(), anyMap());
        verify(questProgress, never()).markCompleted(anyLong(), anyString(), anyLong());
    }

    /* ---------------------------- proof scan idempotency + aggregation ---------------------------- */

    @Test
    void applyProofScanResultIdempotent_skips_when_duplicate_event() {
        when(processedEvents.markProcessedIfNew("cg1", "e1")).thenReturn(false);

        service.applyProofScanResultIdempotent("cg1", "e1", "pk", "CLEAN");

        verifyNoInteractions(submissionProofs);
        verify(submissions, never()).findByProofKey(anyString());
        verify(submissions, never()).findById(anyLong());
    }

    @Test
    void applyProofScanResult_unknown_proofKey_noop() {
        when(submissionProofs.findByProofKey("missing")).thenReturn(Optional.empty());
        when(submissions.findByProofKey("missing")).thenReturn(Optional.empty());

        service.applyProofScanResult("missing", "CLEAN");

        verify(submissions, never()).save(any());
        verify(submissionProofs, never()).save(any(SubmissionProof.class));
    }

    @Test
    void applyProofScanResult_legacy_clean_marks_pending_from_scanning() {
        Submission legacy = sub(10L, 1L, "u1", ReviewStatus.SCANNING);
        legacy.setProofKey("pk1");

        when(submissionProofs.findByProofKey("pk1")).thenReturn(Optional.empty());
        when(submissions.findByProofKey("pk1")).thenReturn(Optional.of(legacy));

        service.applyProofScanResult("pk1", "CLEAN");

        assertThat(legacy.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(legacy.getProofScanStatus()).isEqualTo(ProofScanStatus.CLEAN);
        assertNotNull(legacy.getProofScannedAt());

        verify(submissions).save(legacy);
    }

    @Test
    void applyProofScanResult_legacy_infected_rejects_and_sets_note_when_missing() {
        Submission legacy = sub(11L, 1L, "u1", ReviewStatus.PENDING);
        legacy.setProofKey("pk2");
        legacy.setNote("   ");

        when(submissionProofs.findByProofKey("pk2")).thenReturn(Optional.empty());
        when(submissions.findByProofKey("pk2")).thenReturn(Optional.of(legacy));

        service.applyProofScanResult("pk2", "INFECTED");

        assertThat(legacy.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(legacy.getProofScanStatus()).isEqualTo(ProofScanStatus.INFECTED);
        assertNotNull(legacy.getProofScannedAt());
        assertThat(legacy.getNote()).contains("Proof scan failed: INFECTED");

        verify(submissions).save(legacy);
    }

    @Test
    void applyProofScanResult_newModel_updates_proof_and_when_all_clean_marks_submission_pending() {
        SubmissionProof proof = SubmissionProof.builder()
                .submissionId(500L)
                .proofKey("pk500")
                .scanStatus(ProofScanStatus.PENDING)
                .build();

        Submission s = sub(500L, 42L, "u42", ReviewStatus.SCANNING);

        when(submissionProofs.findByProofKey("pk500")).thenReturn(Optional.of(proof));
        when(submissions.findById(500L)).thenReturn(Optional.of(s));

        when(submissionProofs.countBySubmissionId(500L)).thenReturn(2L);
        when(submissionProofs.countBySubmissionIdAndScanStatusIn(eq(500L), anyList())).thenReturn(0L);
        when(submissionProofs.countBySubmissionIdAndScanStatus(500L, ProofScanStatus.CLEAN)).thenReturn(2L);

        service.applyProofScanResult("pk500", "CLEAN");

        // proof updated
        ArgumentCaptor<SubmissionProof> proofCap = ArgumentCaptor.forClass(SubmissionProof.class);
        verify(submissionProofs).save(proofCap.capture());
        assertThat(proofCap.getValue().getScanStatus()).isEqualTo(ProofScanStatus.CLEAN);
        assertNotNull(proofCap.getValue().getScannedAt());

        // submission aggregated -> PENDING + CLEAN
        assertThat(s.getStatus()).isEqualTo(ReviewStatus.PENDING);
        assertThat(s.getProofScanStatus()).isEqualTo(ProofScanStatus.CLEAN);
        assertNotNull(s.getProofScannedAt());
        verify(submissions).save(s);
    }

    @Test
    void applyProofScanResult_newModel_bad_proof_rejects_and_appends_reason_without_duplication() {
        SubmissionProof proof = SubmissionProof.builder()
                .submissionId(600L)
                .proofKey("pk600")
                .scanStatus(ProofScanStatus.PENDING)
                .build();

        Submission s = sub(600L, 42L, "u42", ReviewStatus.PENDING);
        s.setNote("existing-note");

        when(submissionProofs.findByProofKey("pk600")).thenReturn(Optional.of(proof));
        when(submissions.findById(600L)).thenReturn(Optional.of(s));

        when(submissionProofs.countBySubmissionId(600L)).thenReturn(3L);
        when(submissionProofs.countBySubmissionIdAndScanStatusIn(eq(600L), anyList())).thenReturn(1L);
        when(submissionProofs.countBySubmissionIdAndScanStatus(600L, ProofScanStatus.CLEAN)).thenReturn(1L);

        service.applyProofScanResult("pk600", "INFECTED");

        assertThat(s.getStatus()).isEqualTo(ReviewStatus.REJECTED);
        assertThat(s.getProofScanStatus()).isEqualTo(ProofScanStatus.INFECTED);
        assertNotNull(s.getProofScannedAt());
        assertThat(s.getNote()).contains("existing-note");
        assertThat(s.getNote()).contains("Proof scan failed: INFECTED");

        // second time with same status shouldn't duplicate reason
        reset(submissions);
        when(submissions.findById(600L)).thenReturn(Optional.of(s));
        when(submissionProofs.findByProofKey("pk600")).thenReturn(Optional.of(proof));
        when(submissionProofs.countBySubmissionId(600L)).thenReturn(3L);
        when(submissionProofs.countBySubmissionIdAndScanStatusIn(eq(600L), anyList())).thenReturn(1L);
        when(submissionProofs.countBySubmissionIdAndScanStatus(600L, ProofScanStatus.CLEAN)).thenReturn(1L);

        String before = s.getNote();
        service.applyProofScanResult("pk600", "INFECTED");
        assertThat(s.getNote()).isEqualTo(before);
    }

    @Test
    void applyProofScanResult_newModel_ignores_aggregate_update_for_final_submission_but_still_saves_proof() {
        SubmissionProof proof = SubmissionProof.builder()
                .submissionId(700L)
                .proofKey("pk700")
                .scanStatus(ProofScanStatus.PENDING)
                .build();

        Submission s = sub(700L, 7L, "u7", ReviewStatus.APPROVED);

        when(submissionProofs.findByProofKey("pk700")).thenReturn(Optional.of(proof));
        when(submissions.findById(700L)).thenReturn(Optional.of(s));

        service.applyProofScanResult("pk700", "CLEAN");

        verify(submissionProofs).save(any(SubmissionProof.class));
        verify(submissions, never()).save(any());
        verify(submissionProofs, never()).countBySubmissionId(anyLong());
    }

    @Test
    void applyProofScanResult_newModel_partial_clean_does_not_change_submission_status() {
        SubmissionProof proof = SubmissionProof.builder()
                .submissionId(800L)
                .proofKey("pk800")
                .scanStatus(ProofScanStatus.PENDING)
                .build();

        Submission s = sub(800L, 8L, "u8", ReviewStatus.SCANNING);

        when(submissionProofs.findByProofKey("pk800")).thenReturn(Optional.of(proof));
        when(submissions.findById(800L)).thenReturn(Optional.of(s));

        when(submissionProofs.countBySubmissionId(800L)).thenReturn(2L);
        when(submissionProofs.countBySubmissionIdAndScanStatusIn(eq(800L), anyList())).thenReturn(0L);
        when(submissionProofs.countBySubmissionIdAndScanStatus(800L, ProofScanStatus.CLEAN)).thenReturn(1L); // not all

        service.applyProofScanResult("pk800", "CLEAN");

        verify(submissionProofs).save(any(SubmissionProof.class));
        verify(submissions, never()).save(any()); // no aggregate change yet
        assertThat(s.getStatus()).isEqualTo(ReviewStatus.SCANNING);
    }

    /* ---------------------------- proof keys + urls ---------------------------- */

    @Test
    void proofKeysForSubmission_prefers_submissionProofs_and_filters_nulls() {
        when(submissionProofs.findBySubmissionIdOrderByIdAsc(1L)).thenReturn(List.of(
                SubmissionProof.builder().submissionId(1L).proofKey("a").build(),
                SubmissionProof.builder().submissionId(1L).proofKey(null).build(),
                SubmissionProof.builder().submissionId(1L).proofKey("b").build()
        ));

        List<String> keys = service.proofKeysForSubmission(1L);
        assertThat(keys).containsExactly("a", "b");
        verify(submissions, never()).findById(anyLong());
    }

    @Test
    void proofKeysForSubmission_falls_back_to_legacy_submission_proofKey_when_no_proofs_rows() {
        when(submissionProofs.findBySubmissionIdOrderByIdAsc(2L)).thenReturn(List.of());

        Submission legacy = sub(2L, 1L, "u", ReviewStatus.PENDING);
        legacy.setProofKey("legacy-key");
        when(submissions.findById(2L)).thenReturn(Optional.of(legacy));

        assertThat(service.proofKeysForSubmission(2L)).containsExactly("legacy-key");
    }

    @Test
    void proofKeysForSubmission_returns_empty_when_no_proofs_and_legacy_blank() {
        when(submissionProofs.findBySubmissionIdOrderByIdAsc(3L)).thenReturn(null);

        Submission legacy = sub(3L, 1L, "u", ReviewStatus.PENDING);
        legacy.setProofKey("  ");
        when(submissions.findById(3L)).thenReturn(Optional.of(legacy));

        assertThat(service.proofKeysForSubmission(3L)).isEmpty();
    }

    @Test
    void signedGetUrlsForSubmission_maps_all_keys() {
        when(submissionProofs.findBySubmissionIdOrderByIdAsc(10L)).thenReturn(List.of(
                SubmissionProof.builder().submissionId(10L).proofKey("k1").build(),
                SubmissionProof.builder().submissionId(10L).proofKey("k2").build()
        ));

        when(proofClient.signGet("k1")).thenReturn("S1");
        when(proofClient.signGet("k2")).thenReturn("S2");

        assertThat(service.signedGetUrlsForSubmission(10L)).containsExactly("S1", "S2");
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

    /* ---------------------------- counts ---------------------------- */

    @Test
    void countMine_passthrough() {
        when(submissions.countByUserId("me")).thenReturn(123L);
        assertThat(service.countMine("me")).isEqualTo(123L);
    }
}
