package com.questify.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class JwtAuth {

    private static Jwt asJwt(Authentication auth) {
        if (auth == null) return null;
        var p = auth.getPrincipal();
        return (p instanceof Jwt) ? (Jwt) p : null;
    }

    public String userId(Authentication auth) {
        var jwt = asJwt(auth);
        if (jwt != null) {
            var sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) return sub;
            var uid = jwt.getClaimAsString("user_id");
            if (uid != null && !uid.isBlank()) return uid;
        }
        return Optional.ofNullable(auth != null ? auth.getName() : null)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Missing user identifier in JWT"));
    }

    public String username(Authentication auth) {
        var jwt = asJwt(auth);
        if (jwt != null) {
            var u = jwt.getClaimAsString("preferred_username");
            if (u != null && !u.isBlank()) return u;

            u = jwt.getClaimAsString("username");
            if (u != null && !u.isBlank()) return u;

            var email = jwt.getClaimAsString("email");
            if (email != null && email.contains("@")) return email.substring(0, email.indexOf('@'));

            var sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) return sub;
        }
        return Optional.ofNullable(auth != null ? auth.getName() : null)
                .filter(s -> !s.isBlank())
                .orElse(userId(auth));
    }

    public String email(Authentication auth) {
        var jwt = asJwt(auth);
        return (jwt != null) ? jwt.getClaimAsString("email") : null;
    }

    public String name(Authentication auth) {
        var jwt = asJwt(auth);
        if (jwt != null) {
            var v = jwt.getClaimAsString("name");
            if (v != null && !v.isBlank()) return v;

            v = jwt.getClaimAsString("given_name");
            if (v != null && !v.isBlank()) return v;

            v = jwt.getClaimAsString("preferred_username");
            if (v != null && !v.isBlank()) return v;

            var email = jwt.getClaimAsString("email");
            if (email != null && email.contains("@")) return email.substring(0, email.indexOf('@'));
        }
        return username(auth);
    }

    public String avatar(Authentication auth) {
        var jwt = asJwt(auth);
        if (jwt == null) return null;
        var pic = jwt.getClaimAsString("picture");
        if (pic != null && !pic.isBlank()) return pic;
        pic = jwt.getClaimAsString("avatar_url");
        return (pic != null && !pic.isBlank()) ? pic : null;
    }
}
