package com.questify.tests.service;

import com.questify.config.NotFoundException;
import com.questify.config.StorageProperties;
import com.questify.config.security.CustomUserDetails;
import com.questify.domain.*;
import com.questify.dto.SubmissionDtos;
import com.questify.mapper.SubmissionMapper;
import com.questify.persistence.*;
import com.questify.service.SubmissionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class SubmissionServiceTest {

    @Mock SubmissionRepository submissions;
    @Mock QuestRepository quests;
    @Mock UserRepository users;
    @Mock SubmissionMapper mapper;
    @Mock S3Client s3;
    @Mock S3Presigner presigner;
    @Mock StorageProperties storage;
    @Mock QuestCompletionRepository questCompletions;

    @InjectMocks SubmissionService service;

    /* ----------------------- defaults & helpers ----------------------- */

    @BeforeEach
    void defaults() {
        when(storage.getBucket()).thenReturn("bucket");
        when(storage.getMakeUploadsPublic()).thenReturn(Boolean.TRUE);
        when(storage.getPublicBaseUrl()).thenReturn("https://cdn.local");

        lenient().when(mapper.toRes(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            return new SubmissionDtos.SubmissionRes(
                    s.getId(),
                    s.getQuest() != null ? s.getQuest().getId() : null,
                    s.getUser() != null ? s.getUser().getId() : null,
                    s.getProofText(), s.getProofUrl(), s.getComment(),
                    s.getReviewStatus(), s.getReviewNote(),
                    s.getCreatedAt(), s.getUpdatedAt(),
                    s.getMediaType(), s.getFileSize(),
                    s.getReviewedAt(), s.getReviewerUserId(), s.getClosed()
            );
        });
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    private void mockAuth(Long userId, String... roles) {
        CustomUserDetails cud = mock(CustomUserDetails.class);
        when(cud.getId()).thenReturn(userId);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(cud);
        Set<GrantedAuthority> set = new HashSet<>();
        for (String r : roles) {
            String name = r.startsWith("ROLE_") ? r : "ROLE_" + r;
            set.add(new SimpleGrantedAuthority(name));
        }
        doReturn(set).when(authentication).getAuthorities();
        SecurityContext sc = mock(SecurityContext.class);
        when(sc.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(sc);
    }

    private User user(long id) { User u = new User(); u.setId(id); u.setUsername("u"+id); return u; }

    private Quest quest(long id, Long ownerId) {
        Quest q = new Quest(); q.setId(id); q.setTitle("Q"+id); q.setParticipants(new HashSet<>());
        if (ownerId != null) q.setCreatedBy(user(ownerId));
        return q;
    }

    /* ---------------------------- createFromJson ---------------------------- */

    @Test
    void createFromJson_happy_setsPending_andClosedFalse_andMembershipChecked() {
        mockAuth(7L);
        var req = new SubmissionDtos.CreateSubmissionReq(11L, 7L, "I did it", null, "nice");
        Quest q = quest(11L, 7L); // owner => has quest
        when(quests.findById(11L)).thenReturn(Optional.of(q));
        when(users.findById(7L)).thenReturn(Optional.of(user(7L)));

        Submission mapped = new Submission();
        mapped.setProofText("I did it");
        when(mapper.toEntity(req)).thenReturn(mapped);

        when(submissions.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(99L); return s;
        });

        SubmissionDtos.SubmissionRes out = service.createFromJson(req);

        assertThat(out.id()).isEqualTo(99L);
        verify(submissions).save(argThat(s ->
                s.getQuest()==q && Objects.equals(s.getUser().getId(), 7L)
                        && s.getReviewStatus()==ReviewStatus.PENDING && Boolean.FALSE.equals(s.getClosed())));
    }

    @Test
    void createFromJson_requires_login_and_self_only() {
        var req = new SubmissionDtos.CreateSubmissionReq(1L, 2L, "x", null, null);
        assertThrows(AccessDeniedException.class, () -> service.createFromJson(req));

        mockAuth(3L);
        var bad = new SubmissionDtos.CreateSubmissionReq(1L, 99L, "x", null, null);
        assertThrows(AccessDeniedException.class, () -> service.createFromJson(bad));
        verifyNoInteractions(quests, users, mapper, submissions);
    }

    @Test
    void createFromJson_missingQuest_or_missingUser_404() {
        mockAuth(5L);
        var req = new SubmissionDtos.CreateSubmissionReq(9L, 5L, "x", null, null);
        when(quests.findById(9L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.createFromJson(req));

        when(quests.findById(9L)).thenReturn(Optional.of(quest(9L, 8L)));
        when(users.findById(5L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.createFromJson(req));
    }

    @Test
    void createFromJson_denied_when_user_not_in_quest() {
        mockAuth(4L);
        var req = new SubmissionDtos.CreateSubmissionReq(3L, 4L, "x", null, null);
        Quest q = quest(3L, 9L);
        when(quests.findById(3L)).thenReturn(Optional.of(q));
        when(users.findById(4L)).thenReturn(Optional.of(user(4L)));
        assertThrows(AccessDeniedException.class, () -> service.createFromJson(req));
    }

    @Test
    void createFromJson_requires_text_or_url() {
        mockAuth(2L);
        var bad = new SubmissionDtos.CreateSubmissionReq(1L, 2L, " ", " ", null);
        assertThrows(IllegalArgumentException.class, () -> service.createFromJson(bad));
    }

    /* ---------------------------- createFromMultipart ---------------------------- */

    @Test
    void createFromMultipart_happy_uploads_to_s3_and_saves_submission() {
        mockAuth(10L);
        Quest q = quest(1L, 10L);
        when(quests.findById(1L)).thenReturn(Optional.of(q));
        when(users.findById(10L)).thenReturn(Optional.of(user(10L)));

        MockMultipartFile file = new MockMultipartFile("file", "My Pic.png", "image/png", new byte[]{1,2,3});

        when(submissions.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0); s.setId(55L); return s;
        });

        SubmissionDtos.SubmissionRes out = service.createFromMultipart(1L, "  hello  ", file);
        assertThat(out.id()).isEqualTo(55L);

        ArgumentCaptor<PutObjectRequest> putReq = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(putReq.capture(), any(RequestBody.class));
        assertThat(putReq.getValue().bucket()).isEqualTo("bucket");
        assertThat(putReq.getValue().acl()).isEqualTo(ObjectCannedACL.PUBLIC_READ);
        assertThat(putReq.getValue().contentType()).isEqualTo("image/png");

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissions).save(saved.capture());
        Submission s = saved.getValue();
        assertThat(s.getMediaType()).isEqualTo("image/png");
        assertThat(s.getFileSize()).isEqualTo(3L);
        assertThat(s.getComment()).isEqualTo("hello");
        assertThat(s.getProofUrl()).startsWith("https://cdn.local");
        assertThat(s.getProofObjectKey()).contains("submissions/1/10/");
        assertThat(s.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
    }

    @Test
    void createFromMultipart_unsupported_mime_rejected() {
        mockAuth(10L);
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, 10L)));
        when(users.findById(10L)).thenReturn(Optional.of(user(10L)));

        MockMultipartFile pdf = new MockMultipartFile("file", "proof.pdf", "application/pdf", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> service.createFromMultipart(1L, null, pdf));
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void createFromMultipart_filename_sanitized_and_octetstream_detects_by_extension() {
        mockAuth(3L);
        when(quests.findById(2L)).thenReturn(Optional.of(quest(2L, 3L)));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L)));

        MockMultipartFile file = new MockMultipartFile("file", "..\\weird /name? .JPG", "application/octet-stream", new byte[]{1});

        when(submissions.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));
        service.createFromMultipart(2L, null, file);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().contentType()).isEqualTo("image/jpg");

        ArgumentCaptor<Submission> saved = ArgumentCaptor.forClass(Submission.class);
        verify(submissions).save(saved.capture());
        assertThat(saved.getValue().getProofObjectKey()).doesNotContain(" ")
                .contains("submissions/2/3/")
                .endsWith(".JPG");
    }

    @Test
    void createFromMultipart_s3_failure_wraps_in_runtime() {
        mockAuth(5L);
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, 5L)));
        when(users.findById(5L)).thenReturn(Optional.of(user(5L)));
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});

        doThrow(new RuntimeException("boom")).when(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThrows(RuntimeException.class, () -> service.createFromMultipart(1L, null, file));
    }

    @Test
    void createFromMultipart_requires_login_and_membership_and_file() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[]{1});
        assertThrows(AccessDeniedException.class, () -> service.createFromMultipart(1L, null, file));

        mockAuth(6L);
        when(quests.findById(1L)).thenReturn(Optional.of(quest(1L, 9L))); // not owner
        when(users.findById(6L)).thenReturn(Optional.of(user(6L)));
        assertThrows(AccessDeniedException.class, () -> service.createFromMultipart(1L, null, file));

        assertThrows(IllegalArgumentException.class, () -> service.createFromMultipart(1L, null, new MockMultipartFile("file", new byte[]{})));
    }

    /* ---------------------------- listing/get ---------------------------- */

    @Test
    void getOrThrow_maps_or_404() {
        when(submissions.findById(7L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getOrThrow(7L));

        Submission s = new Submission(); s.setId(7L); s.setQuest(quest(1L,1L)); s.setUser(user(2L));
        when(submissions.findById(7L)).thenReturn(Optional.of(s));
        assertThat(service.getOrThrow(7L)).isNotNull();
    }

    @Test
    void listForQuest_and_byStatus_and_listForUser_and_listAll() {
        Quest q = quest(3L, 1L);
        when(quests.findById(3L)).thenReturn(Optional.of(q));
        when(submissions.findByQuest(eq(q), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(submissions.findByQuestAndReviewStatus(eq(q), eq(ReviewStatus.PENDING), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.listForQuest(3L, null, PageRequest.of(0,5)).getContent()).isEmpty();
        assertThat(service.listForQuest(3L, ReviewStatus.PENDING, PageRequest.of(0,5)).getContent()).isEmpty();

        User u = user(9L);
        when(users.findById(9L)).thenReturn(Optional.of(u));
        when(submissions.findByUser(eq(u), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.listForUser(9L, PageRequest.of(0,5)).getContent()).isEmpty();

        when(submissions.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(submissions.findByReviewStatus(eq(ReviewStatus.APPROVED), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThat(service.listAll(null, PageRequest.of(0,5)).getContent()).isEmpty();
        assertThat(service.listAll(ReviewStatus.APPROVED, PageRequest.of(0,5)).getContent()).isEmpty();
    }

    /* ---------------------------- review & completion maintenance ---------------------------- */

    @Test
    void review_happy_sets_fields_and_updates_completion_to_completed() {
        mockAuth(100L, "REVIEWER");
        Quest q = quest(1L, 10L); User u = user(20L);
        Submission s = new Submission(); s.setId(5L); s.setQuest(q); s.setUser(u); s.setReviewStatus(ReviewStatus.PENDING);
        when(submissions.findById(5L)).thenReturn(Optional.of(s));
        when(submissions.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        when(submissions.existsByQuest_IdAndUser_IdAndReviewStatus(1L, 20L, ReviewStatus.APPROVED)).thenReturn(true);
        when(questCompletions.findByQuest_IdAndUser_Id(1L, 20L)).thenReturn(Optional.empty());
        when(submissions.findApprovedByQuestAndUser(1L, 20L)).thenReturn(List.of(s));

        var req = new SubmissionDtos.ReviewSubmissionReq(ReviewStatus.APPROVED, "ok");
        SubmissionDtos.SubmissionRes out = service.review(5L, req);
        assertThat(out.reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
        assertThat(out.closed()).isTrue();
        verify(questCompletions).save(argThat(qc -> qc.getStatus()== QuestCompletion.CompletionStatus.COMPLETED
                && qc.getSubmission()==s));
    }

    @Test
    void review_rejected_requires_note_and_only_pending_allowed() {
        Submission s = new Submission(); s.setId(6L); s.setQuest(quest(1L,1L)); s.setUser(user(2L)); s.setReviewStatus(ReviewStatus.PENDING);
        when(submissions.findById(6L)).thenReturn(Optional.of(s));
        assertThrows(IllegalArgumentException.class, () -> service.review(6L, new SubmissionDtos.ReviewSubmissionReq(ReviewStatus.REJECTED, "  ")));

        s.setReviewStatus(ReviewStatus.APPROVED);
        assertThrows(IllegalArgumentException.class, () -> service.review(6L, new SubmissionDtos.ReviewSubmissionReq(ReviewStatus.REJECTED, "x")));
    }

    @Test
    void review_revokes_completion_when_no_approved_left() {
        Submission s = new Submission(); s.setId(7L); s.setQuest(quest(2L,1L)); s.setUser(user(3L)); s.setReviewStatus(ReviewStatus.PENDING);
        when(submissions.findById(7L)).thenReturn(Optional.of(s));
        when(submissions.save(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));

        when(submissions.existsByQuest_IdAndUser_IdAndReviewStatus(2L, 3L, ReviewStatus.APPROVED)).thenReturn(false);
        QuestCompletion existing = QuestCompletion.builder().quest(s.getQuest()).user(s.getUser()).status(QuestCompletion.CompletionStatus.COMPLETED).build();
        when(questCompletions.findByQuest_IdAndUser_Id(2L, 3L)).thenReturn(Optional.of(existing));

        service.review(7L, new SubmissionDtos.ReviewSubmissionReq(ReviewStatus.REJECTED, "nope"));
        verify(questCompletions).save(argThat(qc -> qc.getStatus()== QuestCompletion.CompletionStatus.REVOKED));
    }

    /* ---------------------------- presigned URL ---------------------------- */

    @Test
    void buildPresignedProofUrl_owner_ok_and_uses_presigner_when_objectKey() throws MalformedURLException {
        mockAuth(9L);
        Submission s = new Submission(); s.setId(1L); s.setUser(user(9L)); s.setQuest(quest(1L,9L)); s.setProofObjectKey("k");
        when(submissions.findById(1L)).thenReturn(Optional.of(s));

        PresignedGetObjectRequest pres = mock(PresignedGetObjectRequest.class);
        when(pres.url()).thenReturn(new URL("https://signed"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(pres);

        String url = service.buildPresignedProofUrl(1L, Duration.ofSeconds(60));
        assertThat(url).isEqualTo("https://signed");
    }

    @Test
    void buildPresignedProofUrl_returns_publicUrl_when_no_objectKey_and_enforces_auth_and_roles() {
        when(submissions.findById(2L)).thenReturn(Optional.of(new Submission()));
        assertThrows(AccessDeniedException.class, () -> service.buildPresignedProofUrl(2L, Duration.ofSeconds(10)));

        mockAuth(8L);
        Submission s = new Submission(); s.setId(3L); s.setUser(user(7L)); s.setQuest(quest(1L, 99L)); s.setProofUrl("https://public");
        when(submissions.findById(3L)).thenReturn(Optional.of(s));
        assertThrows(AccessDeniedException.class, () -> service.buildPresignedProofUrl(3L, Duration.ofSeconds(10)));

        mockAuth(8L, "REVIEWER");
        assertThat(service.buildPresignedProofUrl(3L, Duration.ofSeconds(10))).isEqualTo("https://public");
    }

    /* ---------------------------- stream proof ---------------------------- */

    @Test
    void streamProof_redirects_when_external_url_and_blocks_unauthorized() {
        Submission s = new Submission(); s.setId(9L); s.setUser(user(1L)); s.setQuest(quest(1L, 1L)); s.setProofUrl("https://public");
        when(submissions.findById(9L)).thenReturn(Optional.of(s));

        assertThrows(AccessDeniedException.class, () -> service.streamProof(9L));

        mockAuth(2L);
        assertThrows(AccessDeniedException.class, () -> service.streamProof(9L));

        mockAuth(1L);
        ResponseEntity<InputStreamResource> resp = service.streamProof(9L);
        assertThat(resp.getStatusCode().value()).isEqualTo(302);
        assertThat(resp.getHeaders().getFirst("Location")).isEqualTo("https://public");
    }

    @Test
    void streamProof_serves_from_s3_with_headers_and_mediaType_inline_vs_attachment() {
        Submission s1 = new Submission(); s1.setId(10L); s1.setUser(user(5L)); s1.setQuest(quest(2L, 5L)); s1.setProofObjectKey("k1"); s1.setMediaType("image/png");
        when(submissions.findById(10L)).thenReturn(Optional.of(s1));
        mockAuth(5L);

        ResponseInputStream<GetObjectResponse> body = mock(ResponseInputStream.class);
        GetObjectResponse meta = mock(GetObjectResponse.class);
        when(body.response()).thenReturn(meta);
        when(meta.contentLength()).thenReturn(4L);
        when(s3.getObject(any(GetObjectRequest.class))).thenReturn(body);

        ResponseEntity<InputStreamResource> r1 = service.streamProof(10L);
        assertThat(r1.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(r1.getHeaders().getFirst("Content-Disposition")).startsWith("inline");
        assertThat(r1.getHeaders().getFirst("Cache-Control")).contains("no-store");
        assertThat(r1.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(Objects.requireNonNull(r1.getHeaders().getContentType()).toString()).isEqualTo("image/png");

        Submission s2 = new Submission(); s2.setId(11L); s2.setUser(user(5L)); s2.setQuest(quest(2L, 5L)); s2.setProofObjectKey("k2"); s2.setMediaType("application/octet-stream");
        when(submissions.findById(11L)).thenReturn(Optional.of(s2));
        ResponseEntity<InputStreamResource> r2 = service.streamProof(11L);
        assertThat(r2.getHeaders().getFirst("Content-Disposition")).startsWith("attachment");
    }
}