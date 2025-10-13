package com.questify.service;

import com.questify.config.NotFoundException;
import com.questify.domain.Role;
import com.questify.domain.User;
import com.questify.dto.UserDtos.*;
import com.questify.mapper.UserMapper;
import com.questify.persistence.UserRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.Set;

@Service
@Transactional
public class UserService {
    private final UserRepository repo;
    private final UserMapper mapper;

    public UserService(UserRepository repo, UserMapper mapper) {
        this.repo = repo; this.mapper = mapper;
    }

    public UserRes create(@Valid CreateUserReq req) {
        if (!StringUtils.hasText(req.username()) || !StringUtils.hasText(req.email())) {
            throw new IllegalArgumentException("Username and email must not be empty");
        }
        if (repo.existsByUsername(req.username())) throw new IllegalArgumentException("Username already exists");
        if (repo.existsByEmail(req.email()))    throw new IllegalArgumentException("Email already exists");

        User u = mapper.toEntity(req);
        if (u.getRoles() == null || u.getRoles().isEmpty()) {
            u.setRoles(Set.of(Role.USER));
        }
        return mapper.toRes(repo.save(u));
    }

    @Transactional(readOnly = true)
    public Optional<UserRes> get(Long id) {
        return repo.findById(id).map(mapper::toRes);
    }

    @Transactional(readOnly = true)
    public UserRes getOrThrow(Long id) {
        return mapper.toRes(repo.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id)));
    }

    @Transactional(readOnly = true)
    public boolean usernameTaken(String username) {
        return repo.existsByUsername(username);
    }
}
