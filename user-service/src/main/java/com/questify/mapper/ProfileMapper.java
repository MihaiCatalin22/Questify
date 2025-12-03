package com.questify.mapper;

import com.questify.domain.UserProfile;
import com.questify.dto.ProfileDtos.ProfileRes;

public class ProfileMapper {
    public static ProfileRes toRes(UserProfile p) {
        return new ProfileRes(
                p.getUserId(), p.getUsername(), p.getDisplayName(), p.getEmail(), p.getAvatarUrl(), p.getBio()
        );
    }
}
