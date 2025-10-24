package com.questify.tests.service;

import com.questify.domain.Role;
import com.questify.domain.User;
import com.questify.dto.UserDtos;
import com.questify.mapper.UserMapper;
import com.questify.persistence.UserRepository;
import com.questify.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
public class UserServiceTest {

    @Mock UserRepository repo;
    @Mock UserMapper mapper;
    @InjectMocks UserService service;

    UserDtos.CreateUserReq req;
    User entity, saved;
    UserDtos.UserRes res;

    @BeforeEach
    void init() {
        req = new UserDtos.CreateUserReq(
                "tester","t@ex.com",
                "$2a$10$0123456789012345678901234567890123456789012345678901",
                "Tester");
        entity = User.builder().username("tester").email("t@ex.com").passwordHash(req.passwordHash()).displayName("Tester").build();
        saved = User.builder().id(1L).username("tester").email("t@ex.com").passwordHash(req.passwordHash()).displayName("Tester").roles(Set.of(Role.USER)).build();
        res = new UserDtos.UserRes(1L,"tester","t@ex.com","Tester",null);
    }

    @Test
    void create_ok_assignsDefaultRole() {
        when(repo.existsByUsername("tester")).thenReturn(false);
        when(repo.existsByEmail("t@ex.com")).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(entity);
        when(repo.save(any(User.class))).thenReturn(saved);
        when(mapper.toRes(saved)).thenReturn(res);

        var out = service.create(req);
        assertThat(out.id()).isEqualTo(1L);
        verify(repo).save(argThat(u -> u.getRoles()!=null && u.getRoles().contains(Role.USER)));
    }

    @Test
    void create_missingUsernameOrEmail_badRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new UserDtos.CreateUserReq("", "t@ex.com", req.passwordHash(), null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new UserDtos.CreateUserReq("tester","", req.passwordHash(), null)));
        verify(repo, never()).save(any());
    }

    @Test
    void create_duplicateUsernameOrEmail_badRequest() {
        when(repo.existsByUsername("tester")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
        reset(repo);
        when(repo.existsByUsername("tester")).thenReturn(false);
        when(repo.existsByEmail("t@ex.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void get_found_mapsToDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(saved));
        when(mapper.toRes(saved)).thenReturn(res);
        assertTrue(service.get(1L).isPresent());
    }

    @Test
    void usernameTaken_delegates() {
        when(repo.existsByUsername("tester")).thenReturn(true);
        assertTrue(service.usernameTaken("tester"));
    }
}

