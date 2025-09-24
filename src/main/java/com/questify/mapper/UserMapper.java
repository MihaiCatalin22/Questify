package com.questify.mapper;

import com.questify.domain.User;
import com.questify.dto.UserDtos;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(UserDtos.CreateUserReq req);

    UserDtos.UserRes toRes (User u);
}
