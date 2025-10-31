package com.questify.tests.service;

import com.questify.config.NotFoundException;
import com.questify.domain.Role;
import com.questify.domain.User;
import com.questify.dto.UserDtos;
import com.questify.mapper.UserMapper;
import com.questify.persistence.UserRepository;
import com.questify.service.UserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserServiceTest {

    @Mock UserRepository repo;
    @Mock UserMapper mapper;
    @InjectMocks UserService service;

    UserDtos.CreateUserReq req;
    User mapped;
    User saved;
    UserDtos.UserRes res;

    /* ----------------------- defaults & helpers ----------------------- */

    @BeforeEach
    void init() {
        req = new UserDtos.CreateUserReq(
                "tester","t@ex.com",
                "$2a$10$0123456789012345678901234567890123456789012345678901",
                "Tester");

        mapped = User.builder()
                .username("tester").email("t@ex.com")
                .passwordHash(req.passwordHash())
                .displayName("Tester")
                .build();

        saved = User.builder()
                .id(1L)
                .username("tester").email("t@ex.com")
                .passwordHash(req.passwordHash())
                .displayName("Tester")
                .roles(new HashSet<>(Set.of(Role.USER)))
                .build();

        lenient().when(mapper.toRes(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            return new UserDtos.UserRes(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), null);
        });
    }

    /* ---------------------------- create ---------------------------- */

    @Test
    void create_ok_assignsDefaultRole_when_mapper_did_not_set_roles() {
        when(repo.existsByUsername("tester")).thenReturn(false);
        when(repo.existsByEmail("t@ex.com")).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(mapped);
        when(repo.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0); u.setId(1L); return u;
        });

        UserDtos.UserRes out = service.create(req);

        assertThat(out.id()).isEqualTo(1L);
        verify(repo).save(argThat(u -> u.getRoles() != null && u.getRoles().contains(Role.USER)));
    }

    @Test
    void create_preserves_roles_if_mapper_already_set() {
        User pre = User.builder()
                .username("tester").email("t@ex.com")
                .passwordHash(req.passwordHash())
                .roles(new HashSet<>(Set.of(Role.ADMIN, Role.REVIEWER)))
                .build();

        when(repo.existsByUsername("tester")).thenReturn(false);
        when(repo.existsByEmail("t@ex.com")).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(pre);
        when(repo.save(any(User.class))).thenAnswer(inv -> { User u = inv.getArgument(0); u.setId(2L); return u; });

        UserDtos.UserRes out = service.create(req);
        assertThat(out.id()).isEqualTo(2L);
        verify(repo).save(argThat(u -> u.getRoles().contains(Role.ADMIN) && u.getRoles().contains(Role.REVIEWER)));
    }

    @Test
    void create_missingUsernameOrEmail_badRequest() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new UserDtos.CreateUserReq("", "t@ex.com", req.passwordHash(), null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new UserDtos.CreateUserReq("tester", "", req.passwordHash(), null)));
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

    /* ---------------------------- get / getOrThrow ---------------------------- */

    @Test
    void get_found_mapsToDto() {
        when(repo.findById(1L)).thenReturn(Optional.of(saved));
        assertTrue(service.get(1L).isPresent());
    }

    @Test
    void getOrThrow_404_when_missing() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getOrThrow(99L));
    }

    /* ---------------------------- list / usernameTaken ---------------------------- */

    @Test
    void list_maps_all_users() {
        User a = User.builder().id(1L).username("a").email("a@ex.com").passwordHash("p").build();
        User b = User.builder().id(2L).username("b").email("b@ex.com").passwordHash("p").build();
        when(repo.findAll()).thenReturn(List.of(a, b));

        var list = service.list();
        assertThat(list).hasSize(2);
        assertThat(list).extracting(UserDtos.UserRes::username).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void usernameTaken_delegates() {
        when(repo.existsByUsername("tester")).thenReturn(true);
        assertTrue(service.usernameTaken("tester"));
    }
}
