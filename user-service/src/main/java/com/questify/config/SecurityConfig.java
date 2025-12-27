package com.questify.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:${internal.token:}}}")
    private String internalToken;

    @Bean
    @Order(0)
    public SecurityFilterChain internal(HttpSecurity http) throws Exception {
        http.securityMatcher(req -> {
                    String p = req.getServletPath();
                    return p != null && p.startsWith("/internal/");
                })
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .httpBasic(h -> h.disable())
                .formLogin(f -> f.disable())
                .addFilterBefore(new InternalTokenFilter(() -> internalToken), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain api(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())))
                .httpBasic(h -> h.disable())
                .formLogin(f -> f.disable());
        return http.build();
    }

    static class InternalTokenFilter extends OncePerRequestFilter {
        interface TokenSupplier { String get(); }
        private final TokenSupplier tokenSupplier;

        InternalTokenFilter(TokenSupplier tokenSupplier) {
            this.tokenSupplier = tokenSupplier;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            String p = request.getServletPath();
            return p == null || !p.startsWith("/internal/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String token = tokenSupplier.get();
            String got = request.getHeader("X-Internal-Token");

            if (token == null || token.isBlank() || got == null || !token.equals(got)) {
                response.setStatus(403);
                return;
            }
            chain.doFilter(request, response);
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        var c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return c;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of(
                "https://*.ts.net",
                "https://questify.tail03c40b.ts.net",
                "https://localhost:5443",
                "https://localhost:5173"
        ));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);

        var src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
