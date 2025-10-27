package com.questify.controller;

import com.questify.config.security.CustomUserDetails;
import com.questify.domain.ReviewStatus;
import com.questify.dto.SubmissionDtos.CreateSubmissionReq;
import com.questify.dto.SubmissionDtos.ReviewSubmissionReq;
import com.questify.dto.SubmissionDtos.SubmissionRes;
import com.questify.service.SubmissionService;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

@CrossOrigin(origins = "https://localhost:5173")
@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService service;

    public SubmissionController(SubmissionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<SubmissionRes> list(
            @RequestParam(required = false) ReviewStatus status,
            @ParameterObject Pageable pageable,
            Authentication authentication) {

        if (hasAnyRole(authentication, "ADMIN", "REVIEWER")) {
            return service.listAll(status, pageable);
        }
        Long currentUserId = currentUserId(authentication);
        return service.listForUser(currentUserId, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public SubmissionRes get(@PathVariable Long id) {
        return service.getOrThrow(id);
    }

    @GetMapping(path = "/../quests/{questId}/submissions")
    @PreAuthorize("isAuthenticated()")
    public Page<SubmissionRes> listForQuestFriendly(
            @PathVariable Long questId,
            @RequestParam(required = false) ReviewStatus status,
            @ParameterObject Pageable pageable,
            Authentication authentication) {
        return service.listForQuest(questId, status, pageable);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SubmissionRes createJson(@Valid @RequestBody CreateSubmissionReq req) {
        return service.createFromJson(req);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SubmissionRes createMultipartAtRoot(
            @RequestParam("questId") Long questId,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam("file") MultipartFile file
    ) {
        return service.createFromMultipart(questId, comment, file);
    }

    @PostMapping(
            path = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("isAuthenticated()")
    public SubmissionRes createMultipart(
            @RequestParam("questId") Long questId,
            @RequestParam(value = "comment", required = false) String comment,
            @RequestParam("file") MultipartFile file
    ) {
        return service.createFromMultipart(questId, comment, file);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public SubmissionRes review(@PathVariable Long id, @Valid @RequestBody ReviewSubmissionReq req) {
        return service.review(id, req);
    }

    private boolean hasAnyRole(Authentication auth, String... roles) {
        if (auth == null || auth.getAuthorities() == null) return false;
        var set = auth.getAuthorities();
        for (String r : roles) {
            String role = r.startsWith("ROLE_") ? r : ("ROLE_" + r);
            boolean ok = set.stream().anyMatch(a -> a.getAuthority().equals(role) || a.getAuthority().equals(r));
            if (ok) return true;
        }
        return false;
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null || auth.getPrincipal() == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof CustomUserDetails cud) return cud.getId();
        return null;
    }

    @GetMapping("/{id}/proof-url")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> proofUrl(@PathVariable Long id, @RequestParam(defaultValue = "300") long ttlSeconds) {
        String url = service.buildPresignedProofUrl(id, Duration.ofSeconds(Math.min(ttlSeconds, 900))); // cap at 15m
        return Map.of("url", url, "expiresInSeconds", Math.min(ttlSeconds, 900));
    }

    @GetMapping("/{id}/proof")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> proof(@PathVariable Long id) {
        return service.streamProof(id);
    }
}
