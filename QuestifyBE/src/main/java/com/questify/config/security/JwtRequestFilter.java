package com.questify.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC = Set.of(
            "/login", "/register", "/auth/login", "/auth/register",
            "/actuator", "/v3/api-docs", "/swagger-ui"
    );

    private final JwtUtil jwt;
    private final UserDetailsService uds;

    public JwtRequestFilter(JwtUtil jwt, UserDetailsService uds){
        this.jwt = jwt;
        this.uds = uds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getServletPath();
        if (PUBLIC.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"))) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            String token = auth.substring(7);
            String username = null;
            try {
                username = jwt.subject(token);
            } catch (Exception ignore) { /* invalid -> continue unauthenticated */ }

            if (username != null) {
                var user = uds.loadUserByUsername(username);
                if (jwt.valid(token, user.getUsername())) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            user, null, user.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        }

        chain.doFilter(req, res);
    }
}
