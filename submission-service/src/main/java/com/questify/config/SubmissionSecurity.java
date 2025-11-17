package com.questify.config;

import com.questify.service.SubmissionService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("submissionSecurity")
public class SubmissionSecurity {

    private final SubmissionService service;

    public SubmissionSecurity(SubmissionService service) {
        this.service = service;
    }

    public boolean canRead(Long id, Authentication auth) {
        if (auth == null) return false;
        if (hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER")) return true;

        var s = service.get(id);
        String me = userId(auth);
        return me != null && me.equals(s.getUserId());
    }

    public boolean canReview(Authentication auth) {
        return hasRole(auth, "ADMIN") || hasRole(auth, "REVIEWER");
    }

    private static boolean hasRole(Authentication auth, String role) {
        if (auth.getAuthorities() == null) return false;
        String full = "ROLE_" + role;
        for (GrantedAuthority a : auth.getAuthorities()) {
            String g = a.getAuthority();
            if (role.equals(g) || full.equals(g)) return true;
        }
        return false;
    }

    private static String userId(Authentication auth) {
        var p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            var uid = jwt.getClaimAsString("user_id");
            return uid != null ? uid : jwt.getSubject();
        }
        return auth.getName();
    }
}