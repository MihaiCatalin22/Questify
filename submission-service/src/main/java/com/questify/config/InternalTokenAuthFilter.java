package com.questify.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class InternalTokenAuthFilter extends OncePerRequestFilter {

    private final Set<String> allowedTokens = new HashSet<>();

    public InternalTokenAuthFilter(
            @Value("${INTERNAL_TOKEN:}") String internalToken,
            @Value("${SECURITY_INTERNAL_TOKEN:}") String securityInternalToken
    ) {
        if (StringUtils.hasText(internalToken)) {
            allowedTokens.add(internalToken.trim());
        }
        if (StringUtils.hasText(securityInternalToken)) {
            allowedTokens.add(securityInternalToken.trim());
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getServletPath(); // e.g. "/internal/quests/..."
        return p == null || !p.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String header = req.getHeader("X-Internal-Token");
        if (!StringUtils.hasText(header)) {
            header = req.getHeader("X-Security-Internal-Token");
        }

        if (allowedTokens.isEmpty() || !StringUtils.hasText(header) || !allowedTokens.contains(header.trim())) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "internal", "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        chain.doFilter(req, res);
    }
}
