package com.questify.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JwtAuth {

    private static Jwt asJwt(Authentication auth) {
        if (auth == null) {
            return null;
        }
        var principal = auth.getPrincipal();
        return principal instanceof Jwt jwt ? jwt : null;
    }

    public String userId(Authentication auth) {
        var jwt = asJwt(auth);
        if (jwt != null) {
            var sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                return sub;
            }
            var uid = jwt.getClaimAsString("user_id");
            if (uid != null && !uid.isBlank()) {
                return uid;
            }
        }
        return Optional.ofNullable(auth != null ? auth.getName() : null)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Missing user identifier in JWT"));
    }
}
