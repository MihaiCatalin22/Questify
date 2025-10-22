package com.questify.config.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {
    @Value("${security.jwt.secret}") private String secretBase64;
    @Value("${security.jwt.expiration-seconds:3600}") private long expSeconds;

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
    }

    public String generate(String subject, Map<String,Object> claims) {
        var now = new Date();
        var exp = new Date(now.getTime() + expSeconds * 1000);
        return Jwts.builder()
                .setClaims(claims).setSubject(subject)
                .setIssuedAt(now).setExpiration(exp)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String subject(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public Date expiration(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getExpiration();
    }

    public boolean valid(String token, String expectedSubject) {
        try {
            return expectedSubject.equals(subject(token)) && expiration(token).after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
