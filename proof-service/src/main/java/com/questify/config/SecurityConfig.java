// proof-service/src/main/java/com/questify/config/SecurityConfig.java
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
import java.util.function.Predicate;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // DO NOT make final; avoid constructor autowiring of String beans.
    @Value("${SECURITY_INTERNAL_TOKEN:${INTERNAL_TOKEN:}}")
    private String internalToken;

    /** Simple prefix test; zero deprecated APIs. */
    private static boolean isInternal(HttpServletRequest req) {
        String ctx = req.getContextPath() == null ? "" : req.getContextPath();
        return req.getRequestURI().startsWith(ctx + "/internal/");
    }

    /** Filter that validates X-Internal-Token for /internal/** */
    static class InternalTokenFilter extends OncePerRequestFilter {
        private final Predicate<HttpServletRequest> match;
        private final String token;

        InternalTokenFilter(Predicate<HttpServletRequest> match, String token) {
            this.match = match; this.token = token;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !match.test(request);
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request,
                HttpServletResponse response,
                FilterChain chain
        ) throws ServletException, IOException {
            String hdr = request.getHeader("X-Internal-Token");
            if (hdr != null && !hdr.isBlank() && hdr.equals(token)) {
                chain.doFilter(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        }
    }

    /** Chain for /internal/** — matched via lambda, protected by our filter. */
    @Bean
    @Order(0)
    SecurityFilterChain internalChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(SecurityConfig::isInternal) // <= lambda matcher
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new InternalTokenFilter(SecurityConfig::isInternal, internalToken),
                        BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Regular API chain — JWT protected. */
    @Bean
    @Order(1)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
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

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        var c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return c;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(List.of("https://localhost:5443"));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        var src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
