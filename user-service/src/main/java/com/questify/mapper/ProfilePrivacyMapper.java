package com.questify.mapper;

import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.ProfileRes;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class ProfilePrivacyMapper {

    private ProfilePrivacyMapper() {}

    public static ProfileRes toResForViewer(UserProfile p, Authentication auth) {
        if (p == null) return null;

        if (p.isDeleted()) {
            return new ProfileRes(
                    p.getUserId(),
                    p.getUsername(),
                    "Deleted user",
                    null,
                    null,
                    null
            );
        }

        boolean isSelf = false;
        boolean isPrivileged = false;

        if (auth != null) {
            isSelf = p.getUserId() != null && p.getUserId().equals(auth.getName());

            if (auth.getAuthorities() != null) {
                for (GrantedAuthority a : auth.getAuthorities()) {
                    String au = a.getAuthority();
                    if ("ROLE_ADMIN".equals(au) || "ADMIN".equals(au)
                            || "ROLE_REVIEWER".equals(au) || "REVIEWER".equals(au)) {
                        isPrivileged = true;
                        break;
                    }
                }
            }
        }

        String email = (isSelf || isPrivileged) ? p.getEmail() : null;

        return new ProfileRes(
                p.getUserId(),
                p.getUsername(),
                p.getDisplayName(),
                email,
                p.getAvatarUrl(),
                p.getBio()
        );
    }
}