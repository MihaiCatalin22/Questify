package com.questify.controller;

import com.questify.config.JwtAuth;
import com.questify.dto.CoachDtos.CoachSuggestionsReq;
import com.questify.service.CoachService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/coach")
public class CoachController {

    private final CoachService coachService;
    private final JwtAuth jwtAuth;

    public CoachController(CoachService coachService, JwtAuth jwtAuth) {
        this.coachService = coachService;
        this.jwtAuth = jwtAuth;
    }

    @PostMapping("/suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> suggestions(@Valid @RequestBody(required = false) CoachSuggestionsReq request,
                                         Authentication authentication) {
        CoachSuggestionsReq effectiveRequest = request == null
                ? new CoachSuggestionsReq(null, null)
                : request;
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(coachService.generateSuggestions(jwtAuth.userId(authentication), effectiveRequest));
    }
}
