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
            var uid = jwt.getClaimAsString("user_id");
            return uid != null ? uid : jwt.getSubject();
        }
        return auth.getName();
    }

    public String username(Authentication auth) {
        if (auth == null) return null;
        var p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            var u = jwt.getClaimAsString("preferred_username");
            return u != null ? u : jwt.getClaimAsString("username");
        }
        return auth.getName();
    }

    public String email(Authentication auth) {
        if (auth == null) return null;
        var p = auth.getPrincipal();
        if (p instanceof Jwt jwt) return jwt.getClaimAsString("email");
        return null;
    }

    public String name(Authentication auth) {
        if (auth == null) return null;
        var p = auth.getPrincipal();
        if (p instanceof Jwt jwt) return jwt.getClaimAsString("name");
        return null;
    }
}
