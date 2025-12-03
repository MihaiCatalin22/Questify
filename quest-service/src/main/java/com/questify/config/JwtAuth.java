package com.questify.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtAuth {
    public String userId(Authentication auth) {
        if (auth == null) return null;
        var p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            var uid = jwt.getClaimAsString("user_id"); // e.g., Keycloak
            return uid != null ? uid : jwt.getSubject();
        }
        return auth.getName();
    }
}