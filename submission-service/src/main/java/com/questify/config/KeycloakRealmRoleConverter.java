package com.questify.config;

import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakRealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();

        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rs = realmAccess.get("roles");
            if (rs instanceof Collection<?> c) {
                for (Object r : c) roles.add(String.valueOf(r));
            }
        }

        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            for (Object v : resourceAccess.values()) {
                if (v instanceof Map<?, ?> m) {
                    Object cr = m.get("roles");
                    if (cr instanceof Collection<?> c) {
                        for (Object r : c) roles.add(String.valueOf(r));
                    }
                }
            }
        }

        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        for (String r : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
        }
        return authorities;
    }
}
