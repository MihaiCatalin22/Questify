package com.questify.controller;

import com.questify.client.QuestAccessClient;
import com.questify.config.JwtAuth;
import com.questify.domain.ReviewStatus;
import com.questify.domain.Submission;
import com.questify.dto.SubmissionDtos.*;
import com.questify.mapper.SubmissionMapper;
import com.questify.service.SubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    private final SubmissionService service;
    private final JwtAuth jwt;
    private final QuestAccessClient questAccess;

    public record SubmissionSummaryRes(long submissionsTotal) {}

    public SubmissionController(SubmissionService service, JwtAuth jwt, QuestAccessClient questAccess) {
        this.service = service;
        this.jwt = jwt;
        this.questAccess = questAccess;
    }

    @GetMapping("/mine/summary")
    @PreAuthorize("isAuthenticated()")
    public SubmissionSummaryRes mineSummary(Authentication auth) {
        var me = jwt.userId(auth);
        return new SubmissionSummaryRes(service.countMine(me));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubmissionRes> createJson(@Valid @RequestBody CreateSubmissionReq req, Authentication auth) {
        var saved = service.create(jwt.userId(auth), req);
        return ResponseEntity.created(URI.create("/submissions/" + saved.getId()))
                .body(SubmissionMapper.toRes(saved));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<SubmissionRes> createMultipart(
            @RequestParam("questId") Long questId,
            @RequestParam(value = "comment", required = false) String comment,

            // New: multi
            @RequestParam(value = "files", required = false) List<MultipartFile> files,

            // Back-compat: single
            @RequestParam(value = "file", required = false) MultipartFile file,

            Authentication auth,
            @RequestHeader(name = "Authorization", required = false) String authorization
    ) {
        String bearer = (authorization != null && authorization.startsWith("Bearer "))
                ? authorization.substring(7)
                : null;

        List<MultipartFile> all = new ArrayList<>();
        if (file != null && !file.isEmpty()) all.add(file);
        if (files != null) {
            for (var f : files) {
                if (f != null && !f.isEmpty()) all.add(f);
            }
        }

        if (all.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one file is required");
        }

        var saved = (all.size() == 1)
                ? service.createFromMultipart(questId, comment, all.get(0), jwt.userId(auth), bearer)
                : service.createFromMultipartMany(questId, comment, all, jwt.userId(auth), bearer);

        return ResponseEntity.created(URI.create("/submissions/" + saved.getId()))
                .body(SubmissionMapper.toRes(saved));
    }

    @GetMapping("/quest/{questId}")
    public PageImpl<SubmissionRes> forQuest(@PathVariable Long questId,
                                            @RequestParam(defaultValue="0") int page,
                                            @RequestParam(defaultValue="10") int size,
                                            Authentication auth) {
        var userId = jwt.userId(auth);
        boolean elevated = hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER");
        if (!(elevated || questAccess.allowed(userId, questId))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        var p = service.forQuest(questId, page, size).map(SubmissionMapper::toRes);
        return new PageImpl<>(p.getContent(), p.getPageable(), p.getTotalElements());
    }

    private static boolean hasRole(Authentication auth, String role) {
        if (auth == null || auth.getAuthorities() == null) return false;
        var full = "ROLE_" + role;
        return auth.getAuthorities().stream()
                .anyMatch(a -> role.equals(a.getAuthority()) || full.equals(a.getAuthority()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@submissionSecurity.canRead(#id, authentication)")
    public SubmissionRes byId(@PathVariable Long id) {
        return SubmissionMapper.toRes(service.get(id));
    }

    @GetMapping("/mine")
    @PreAuthorize("isAuthenticated()")
    public PageImpl<SubmissionRes> mine(@RequestParam(defaultValue="0") int page,
                                        @RequestParam(defaultValue="10") int size,
                                        Authentication auth) {
        var p = service.mine(jwt.userId(auth), page, size).map(SubmissionMapper::toRes);
        return new PageImpl<>(p.getContent(), p.getPageable(), p.getTotalElements());
    }

    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public PageImpl<SubmissionRes> pending(@RequestParam(defaultValue="0") int page,
                                           @RequestParam(defaultValue="10") int size) {
        var p = service.pending(page, size).map(SubmissionMapper::toRes);
        return new PageImpl<>(p.getContent(), p.getPageable(), p.getTotalElements());
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public SubmissionRes review(@PathVariable Long id,
                                @Valid @RequestBody ReviewReq req,
                                Authentication auth) {
        var reviewed = service.review(id, req, jwt.userId(auth));
        return SubmissionMapper.toRes(reviewed);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public PageImpl<SubmissionRes> list(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size,
                                        @RequestParam(name = "status", required = false) ReviewStatus status,
                                        Authentication auth) {

        boolean elevated = hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER");

        Page<Submission> data;
        if (elevated) {
            data = (status != null)
                    ? service.byStatus(status, page, size)
                    : service.all(page, size);
        } else {
            data = service.mine(jwt.userId(auth), page, size);
        }

        var mapped = data.map(SubmissionMapper::toRes);
        return new PageImpl<>(mapped.getContent(), data.getPageable(), data.getTotalElements());
    }

    @GetMapping("/{id}/proof")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> proof(@PathVariable Long id, Authentication auth) {
        var s = service.get(id);
        var userId = jwt.userId(auth);
        boolean elevated = hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER");
        boolean allowed = elevated || userId.equals(s.getUserId()) || questAccess.allowed(userId, s.getQuestId());
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");

        String key = s.getProofKey();
        if (key == null || key.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No proof");

        String signed = service.signedGetUrl(key);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(signed)).build();
    }

    @GetMapping("/{id}/proof-url")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> proofUrl(@PathVariable Long id, Authentication auth) {
        var s = service.get(id);
        var userId = jwt.userId(auth);
        boolean elevated = hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER");
        boolean allowed = elevated || userId.equals(s.getUserId()) || questAccess.allowed(userId, s.getQuestId());
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");

        String key = s.getProofKey();
        if (key == null || key.isBlank()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No proof");

        String signed = service.signedGetUrl(key);
        return Map.of("url", signed, "expiresInSeconds", 900);
    }

    @GetMapping("/{id}/proof-urls")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> proofUrls(@PathVariable Long id, Authentication auth) {
        var s = service.get(id);
        var userId = jwt.userId(auth);
        boolean elevated = hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER");
        boolean allowed = elevated || userId.equals(s.getUserId()) || questAccess.allowed(userId, s.getQuestId());
        if (!allowed) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");

        var urls = service.signedGetUrlsForSubmission(id);
        if (urls == null || urls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No proof");
        }

        return Map.of("urls", urls, "expiresInSeconds", 900);
    }
}
