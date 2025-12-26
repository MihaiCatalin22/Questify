package com.questify.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${app.keycloak.admin.baseUrl:http://keycloak:8080/auth}")
    private String baseUrl;

    @Value("${app.keycloak.admin.realm:questify}")
    private String realm;

    @Value("${app.keycloak.admin.clientId:user-service-admin}")
    private String clientId;

    @Value("${app.keycloak.admin.clientSecret:}")
    private String clientSecret;

    private final RestTemplate http = new RestTemplate();

    private record TokenRes(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") long expiresIn
    ) {}

    private record CachedToken(String token, Instant expiresAt) {}
    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    private String token() {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("Keycloak admin client secret is not configured");
        }

        var c = cache.get();
        if (c != null && c.expiresAt().isAfter(Instant.now().plusSeconds(10))) {
            return c.token();
        }

        String tokenUrl = baseUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<TokenRes> resp = http.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                TokenRes.class
        );

        TokenRes body = resp.getBody();
        if (body == null || body.accessToken() == null || body.accessToken().isBlank()) {
            throw new IllegalStateException("Keycloak token response missing access_token");
        }

        Instant expiresAt = Instant.now().plusSeconds(Math.max(30, body.expiresIn()));
        cache.set(new CachedToken(body.accessToken(), expiresAt));
        return body.accessToken();
    }

    private String usersUrl() {
        return baseUrl + "/admin/realms/" + realm + "/users";
    }

    public void disableAndLogout(String userId) {
        String t = token();

        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(t);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        h.setContentType(MediaType.APPLICATION_JSON);

        String userUrl = usersUrl() + "/" + userId;

        http.exchange(
                userUrl,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", false), h),
                Void.class
        );

        http.exchange(
                userUrl + "/logout",
                HttpMethod.POST,
                new HttpEntity<Void>(h),
                Void.class
        );
    }
}
