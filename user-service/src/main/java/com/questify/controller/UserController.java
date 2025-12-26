package com.questify.controller;

import com.questify.config.JwtAuth;
import com.questify.dto.ProfileDtos.*;
import com.questify.mapper.ProfilePrivacyMapper;
import com.questify.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserProfileService service;
    private final JwtAuth jwt;

    public UserController(UserProfileService service, JwtAuth jwt) {
        this.service = service;
        this.jwt = jwt;
    }

    record ApiError(String message) {}

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ProfileRes> me(Authentication auth) {
        var id      = jwt.userId(auth);
        var uname   = jwt.username(auth);
        var display = jwt.name(auth);
        var email   = jwt.email(auth);
        var avatar  = jwt.avatar(auth);

        var p = service.ensure(id, uname, display, email, avatar);

        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ProfilePrivacyMapper.toResForViewer(p, auth));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateMe(@Valid @RequestBody UpsertMeReq req, Authentication auth) {
        var id = jwt.userId(auth);

        try {
            var p = service.upsertMe(id, req);
            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(ProfilePrivacyMapper.toResForViewer(p, auth));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.GONE)
                    .header("Cache-Control", "no-store")
                    .body(new ApiError("Profile is deleted"));
        }
    }

    @GetMapping("/me/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExportRes> exportMe(Authentication auth) {
        var id      = jwt.userId(auth);
        var uname   = jwt.username(auth);
        var display = jwt.name(auth);
        var email   = jwt.email(auth);
        var avatar  = jwt.avatar(auth);

        service.ensure(id, uname, display, email, avatar);

        var res = service.exportMe(id);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(res);
    }

    @DeleteMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeleteMeRes> deleteMe(Authentication auth) {
        var id      = jwt.userId(auth);
        var uname   = jwt.username(auth);
        var display = jwt.name(auth);
        var email   = jwt.email(auth);
        var avatar  = jwt.avatar(auth);

        service.ensure(id, uname, display, email, avatar);

        var res = service.deleteMe(id);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(res);
    }

    @GetMapping("/{id}")
    public ProfileRes byId(@PathVariable String id, Authentication auth) {
        return ProfilePrivacyMapper.toResForViewer(service.get(id), auth);
    }

    @GetMapping
    public java.util.List<ProfileRes> search(@RequestParam(name = "username", defaultValue = "") String q,
                                             Authentication auth) {
        return service.search(q).stream()
                .map(p -> ProfilePrivacyMapper.toResForViewer(p, auth))
                .collect(Collectors.toList());
    }

    @PostMapping("/bulk")
    public BulkResponse bulk(@Valid @RequestBody BulkRequest req, Authentication auth) {
        var list = service.bulk(req.ids()).stream()
                .map(p -> ProfilePrivacyMapper.toResForViewer(p, auth))
                .toList();
        return new BulkResponse(list);
    }
}
