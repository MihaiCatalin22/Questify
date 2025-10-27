package com.questify.service;

import com.questify.config.NotFoundException;
import com.questify.config.security.CustomUserDetails;
import com.questify.config.StorageProperties;
import com.questify.domain.*;
import com.questify.domain.QuestCompletion.CompletionStatus;
import com.questify.dto.SubmissionDtos;
import com.questify.mapper.SubmissionMapper;
import com.questify.persistence.QuestCompletionRepository;
import com.questify.persistence.QuestRepository;
import com.questify.persistence.SubmissionRepository;
import com.questify.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

@Service
@Transactional
public class SubmissionService {

    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of("image", "video");

    private final SubmissionRepository submissions;
    private final QuestRepository quests;
    private final UserRepository users;
    private final SubmissionMapper mapper;
    private final S3Client s3;
    private final S3Presigner presigner;
    private final StorageProperties storage;
    private final QuestCompletionRepository questCompletions;

    public SubmissionService(
            SubmissionRepository submissions,
            QuestRepository quests,
            UserRepository users,
            SubmissionMapper mapper,
            S3Client s3,
            S3Presigner presigner,
            StorageProperties storage,
            QuestCompletionRepository questCompletions
    ) {
        this.submissions = submissions;
        this.quests = quests;
        this.users = users;
        this.mapper = mapper;
        this.s3 = s3;
        this.presigner = presigner;
        this.storage = storage;
        this.questCompletions = questCompletions;
    }

    private boolean isAdminOrReviewer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String g = a.getAuthority();
            if ("ADMIN".equals(g) || "ROLE_ADMIN".equals(g) ||
                    "REVIEWER".equals(g) || "ROLE_REVIEWER".equals(g)) {
                return true;
            }
        }
        return false;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails cud)) return null;
        return cud.getId();
    }

    private boolean userHasQuest(Quest q, Long userId) {
        if (q.getCreatedBy() != null && Objects.equals(q.getCreatedBy().getId(), userId)) return true;
        return q.getParticipants() != null
                && q.getParticipants().stream().anyMatch(u -> Objects.equals(u.getId(), userId));
    }

    private Quest requireQuest(Long questId) {
        return quests.findById(questId).orElseThrow(() -> new NotFoundException("Quest not found: " + questId));
    }

    private User requireUser(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    public SubmissionDtos.SubmissionRes createFromJson(@Valid SubmissionDtos.CreateSubmissionReq req) {
        if (!StringUtils.hasText(req.proofUrl()) && !StringUtils.hasText(req.proofText())) {
            throw new IllegalArgumentException("Provide either proofUrl or proofText");
        }

        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");
        if (req.userId() != null && !me.equals(req.userId())) {
            throw new AccessDeniedException("You can only submit as yourself.");
        }

        Quest q = requireQuest(req.questId());
        User u = requireUser(me);
        if (!userHasQuest(q, me)) throw new AccessDeniedException("You don't have this quest.");

        Submission s = mapper.toEntity(req);
        s.setQuest(q);
        s.setUser(u);
        s.setReviewStatus(ReviewStatus.PENDING);
        s.setClosed(false);

        return mapper.toRes(submissions.save(s));
    }

    private String effectiveContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (StringUtils.hasText(ct) && !"application/octet-stream".equalsIgnoreCase(ct)) {
            return ct;
        }
        String name = file.getOriginalFilename();
        if (name == null) return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        String lower = name.toLowerCase();
        if (lower.endsWith(".jpg")) return "image/jpg";
        if (lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".heic")) return "image/heic";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".m4v")) return "video/x-m4v";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public SubmissionDtos.SubmissionRes createFromMultipart(Long questId, String comment, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided.");
        }

        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");

        Quest q = requireQuest(questId);
        User u = requireUser(me);
        if (!userHasQuest(q, me)) throw new AccessDeniedException("You don't have this quest.");

        String contentType = effectiveContentType(file);
        if (!isAllowedMime(contentType)) {
            throw new IllegalArgumentException("Unsupported file type. Only images and videos are allowed.");
        }

        String safeName = sanitizeFilename(file.getOriginalFilename());
        String key = "submissions/" + questId + "/" + me + "/" + Instant.now().toEpochMilli() + "-" + safeName;

        PutObjectRequest.Builder putBuilder = PutObjectRequest.builder()
                .bucket(storage.getBucket())
                .key(key)
                .contentType(contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);

        if (Boolean.TRUE.equals(storage.getMakeUploadsPublic())) {
            putBuilder.acl(ObjectCannedACL.PUBLIC_READ);
        }

        try {
            s3.putObject(putBuilder.build(), RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (Exception e) {
            throw new RuntimeException("Failed to store file to object storage.", e);
        }

        String publicUrl = buildPublicUrl(key);

        Submission s = new Submission();
        s.setQuest(q);
        s.setUser(u);
        s.setComment(StringUtils.hasText(comment) ? comment.trim() : null);
        s.setProofUrl(publicUrl);
        s.setMediaType(contentType); // keep normalized CT
        s.setFileSize(file.getSize());
        s.setProofObjectKey(key);
        s.setReviewStatus(ReviewStatus.PENDING);
        s.setClosed(false);

        return mapper.toRes(submissions.save(s));
    }

    @Transactional(readOnly = true)
    public SubmissionDtos.SubmissionRes getOrThrow(Long id) {
        return mapper.toRes(submissions.findById(id)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + id)));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SubmissionDtos.SubmissionRes> listForQuest(
            Long questId, ReviewStatus status, org.springframework.data.domain.Pageable pageable) {
        Quest q = requireQuest(questId);
        var page = (status == null)
                ? submissions.findByQuest(q, pageable)
                : submissions.findByQuestAndReviewStatus(q, status, pageable);
        return page.map(mapper::toRes);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SubmissionDtos.SubmissionRes> listForUser(
            Long userId, org.springframework.data.domain.Pageable pageable) {
        User u = requireUser(userId);
        return submissions.findByUser(u, pageable).map(mapper::toRes);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SubmissionDtos.SubmissionRes> listAll(
            ReviewStatus status, org.springframework.data.domain.Pageable pageable) {
        var page = (status == null)
                ? submissions.findAll(pageable)
                : submissions.findByReviewStatus(status, pageable);
        return page.map(mapper::toRes);
    }

    public SubmissionDtos.SubmissionRes review(Long submissionId, @Valid SubmissionDtos.ReviewSubmissionReq req) {
        Submission s = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));
        if (s.getReviewStatus() != ReviewStatus.PENDING) {
            throw new IllegalArgumentException("Only PENDING submissions can be reviewed");
        }
        if (req.reviewStatus() == ReviewStatus.REJECTED && !StringUtils.hasText(req.reviewNote())) {
            throw new IllegalArgumentException("Review note is required when rejecting");
        }

        s.setReviewStatus(req.reviewStatus());
        s.setReviewNote(req.reviewNote());
        s.setReviewedAt(Instant.now());
        s.setClosed(req.reviewStatus() != ReviewStatus.PENDING);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            s.setReviewerUserId(cud.getId());
        }

        s.setUpdatedAt(Instant.now());

        Submission saved = submissions.save(s);

        maintainQuestCompletionFor(saved);

        return mapper.toRes(saved);
    }

    private void maintainQuestCompletionFor(Submission savedSubmission) {
        Quest quest = savedSubmission.getQuest();
        User user = savedSubmission.getUser();

        boolean hasApproved = submissions.existsByQuest_IdAndUser_IdAndReviewStatus(
                quest.getId(), user.getId(), ReviewStatus.APPROVED);

        var existingOpt = questCompletions.findByQuest_IdAndUser_Id(quest.getId(), user.getId());
        Instant now = Instant.now();

        if (hasApproved) {
            QuestCompletion qc = existingOpt.orElseGet(() -> QuestCompletion.builder()
                    .quest(quest)
                    .user(user)
                    .completedAt(now)
                    .build());

            qc.setStatus(CompletionStatus.COMPLETED);
            qc.setUpdatedAt(now);

            submissions.findApprovedByQuestAndUser(quest.getId(), user.getId()).stream().findFirst()
                    .ifPresent(qc::setSubmission);

            questCompletions.save(qc);
        } else if (existingOpt.isPresent()) {
            QuestCompletion qc = existingOpt.get();
            qc.setStatus(CompletionStatus.REVOKED);
            qc.setUpdatedAt(now);
            questCompletions.save(qc);
        }
    }

    private boolean isAllowedMime(String contentType) {
        if (!StringUtils.hasText(contentType)) return false;
        try {
            MimeType mt = MimeTypeUtils.parseMimeType(contentType);
            return ALLOWED_TOP_LEVEL.contains(mt.getType());
        } catch (Exception e) {
            return false;
        }
    }

    private String sanitizeFilename(String original) {
        String base = StringUtils.hasText(original) ? original : "upload";
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");

        if (base.isEmpty()) base = "upload";
        if (base.length() > 120) {
            String ext = "";
            int dot = base.lastIndexOf('.');
            if (dot > 0 && dot < base.length() - 1) {
                ext = base.substring(dot);
                base = base.substring(0, dot);
            }
            base = base.substring(0, Math.min(base.length(), 120 - ext.length())) + ext;
        }
        return base;
    }

    @Transactional(readOnly = true)
    public String buildPresignedProofUrl(Long submissionId, Duration ttl) {
        Submission s = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));
        Long me = currentUserId();
        if (me == null) throw new AccessDeniedException("Login required.");

        boolean allowed = isAdminOrReviewer() || (s.getUser() != null && Objects.equals(s.getUser().getId(), me));
        if (!allowed) throw new AccessDeniedException("Not allowed to view this proof.");

        if (!StringUtils.hasText(s.getProofObjectKey())) {
            return s.getProofUrl();
        }

        var get = GetObjectRequest.builder()
                .bucket(storage.getBucket())
                .key(s.getProofObjectKey())
                .build();

        var presign = GetObjectPresignRequest.builder()
                .signatureDuration(ttl != null ? ttl : Duration.ofMinutes(5))
                .getObjectRequest(get)
                .build();

        return presigner.presignGetObject(presign).url().toString();
    }

    private String buildPublicUrl(String key) {
        if (StringUtils.hasText(storage.getPublicBaseUrl())) {
            String encKey = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
            String base = storage.getPublicBaseUrl().endsWith("/")
                    ? storage.getPublicBaseUrl()
                    : storage.getPublicBaseUrl() + "/";
            return base + encKey;
        }
        return "https://localhost:9000/" + storage.getBucket() + "/" + key;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<InputStreamResource> streamProof(Long submissionId) {
        Submission s = submissions.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

        Long me = currentUserId();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean reviewer = auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream().anyMatch(a ->
                "REVIEWER".equals(a.getAuthority()) || "ROLE_REVIEWER".equals(a.getAuthority())
                        || "ADMIN".equals(a.getAuthority())    || "ROLE_ADMIN".equals(a.getAuthority()));

        if (!reviewer) {
            boolean allowed = Objects.equals(me, s.getUser().getId())
                    || Objects.equals(me, s.getQuest().getCreatedBy() != null ? s.getQuest().getCreatedBy().getId() : null)
                    || (s.getQuest().getParticipants() != null && s.getQuest().getParticipants().stream()
                    .anyMatch(u -> Objects.equals(u.getId(), me)));
            if (!allowed) throw new AccessDeniedException("Not allowed to view this proof.");
        }

        if (StringUtils.hasText(s.getProofObjectKey())) {
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(storage.getBucket())
                    .key(s.getProofObjectKey())
                    .build();

            ResponseInputStream<GetObjectResponse> body = s3.getObject(req);
            long contentLen = body.response().contentLength();
            MediaType mt = MediaType.APPLICATION_OCTET_STREAM;
            if (StringUtils.hasText(s.getMediaType())) {
                try { mt = MediaType.parseMediaType(s.getMediaType()); } catch (Exception ignore) {}
            }

            boolean inline = mt.getType().equalsIgnoreCase("image") || mt.getType().equalsIgnoreCase("video");
            String disp = (inline ? "inline" : "attachment") + "; filename=\"proof-" + s.getId() + "\"";

            HttpHeaders headers = new HttpHeaders();
            headers.setCacheControl("no-store, max-age=0");
            headers.add("Pragma", "no-cache");
            headers.add("X-Content-Type-Options", "nosniff");
            headers.add(HttpHeaders.CONTENT_DISPOSITION, disp);

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mt)
                    .contentLength(contentLen >= 0 ? contentLen : -1)
                    .body(new InputStreamResource(body));
        }

        if (StringUtils.hasText(s.getProofUrl())) {
            return ResponseEntity.status(302).header("Location", s.getProofUrl()).build();
        }

        throw new NotFoundException("Submission has no proof.");
    }
}
