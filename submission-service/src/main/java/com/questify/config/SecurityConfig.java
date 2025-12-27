package com.questify.config;

import lombok.RequiredArgsConstructor;
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

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static String firstNonBlank(String... vals) {
        if (vals == null) return "";
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }

    @Bean
    InternalTokenAuthFilter internalTokenAuthFilter(
            @Value("${INTERNAL_TOKEN:}") String internalToken,
            @Value("${SECURITY_INTERNAL_TOKEN:}") String securityInternalToken,
            @Value("${internal.token:}") String internalDotToken
    ) {
        String resolved = firstNonBlank(securityInternalToken, internalToken, internalDotToken);

        return new InternalTokenAuthFilter(resolved, resolved);
    }

    @Bean
    @Order(0)
    SecurityFilterChain internalChain(HttpSecurity http,
                                      InternalTokenAuthFilter internalTokenAuthFilter) throws Exception {
        http
                // IMPORTANT: do NOT use servletPath; use pattern matcher
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .httpBasic(h -> h.disable())
                .formLogin(f -> f.disable())
                .addFilterBefore(internalTokenAuthFilter, BasicAuthenticationFilter.class);
        return http.build();
    }

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
        cfg.setAllowedOriginPatterns(List.of(
                "https://*.ts.net",
                "https://questify.tail03c40b.ts.net",
                "https://localhost:5443"
        ));
        cfg.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        var src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
