// src/main/java/com/questify/controller/UserController.java
package com.questify.controller;

import com.questify.config.JwtAuth;
import com.questify.dto.ProfileDtos.*;
import com.questify.mapper.ProfilePrivacyMapper;
import com.questify.service.UserProfileService;
import jakarta.validation.Valid;
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
    public ResponseEntity<ProfileRes> updateMe(@Valid @RequestBody UpsertMeReq req, Authentication auth) {
        var id = jwt.userId(auth);
        var p  = service.upsertMe(id, req);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(ProfilePrivacyMapper.toResForViewer(p, auth));
    }

    @GetMapping("/{id}")
    public ProfileRes byId(@PathVariable String id, Authentication auth) {
        return ProfilePrivacyMapper.toResForViewer(service.get(id), auth);
    }

    @GetMapping
    public java.util.List<ProfileRes> search(@RequestParam(name="username", defaultValue="") String q,
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
