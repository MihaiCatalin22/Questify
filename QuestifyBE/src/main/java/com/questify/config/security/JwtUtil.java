package com.questify.config.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${security.jwt.secret}")
    private String secretKey;

    @Value("${security.jwt.ttlMillis:36000000}")
    private long ttlMillis;

    @Value("${security.jwt.clockSkewSeconds:120}")
    private long clockSkewSeconds;

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private JwtParser getParser() {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .setAllowedClockSkewSeconds(clockSkewSeconds)
                .build();
    }

    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + ttlMillis))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token, String username) {
        try {
            final String usernameFromToken = extractUsername(token);
            return username != null
                    && username.equals(usernameFromToken)
                    && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            final Date expiration = extractAllClaims(token).getExpiration();
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    public String extractUsername(String token) {
        try {
            return extractClaim(token, Claims::getSubject);
        } catch (ExpiredJwtException ex) {
            // expired but otherwise valid -> subject still available
            return ex.getClaims() != null ? ex.getClaims().getSubject() : null;
        } catch (JwtException ex) {
            return null;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return getParser()
                .parseClaimsJws(token)
                .getBody();
    }

    public Instant expiration(String token) {
        try {
            return extractAllClaims(token).getExpiration().toInstant();
        } catch (JwtException e) {
            return Instant.EPOCH;
        }
    }
}
