package com.questify.controller;

import com.questify.dto.UserDtos;
import com.questify.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/users")
public class UserController {
    private final UserService service;
    public UserController(UserService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDtos.UserRes create(@Valid @RequestBody UserDtos.CreateUserReq req) {
        return service.create(req);
    }

    @GetMapping("/{id}")
    public UserDtos.UserRes get(@PathVariable Long id) {
        return service.getOrThrow(id);
    }

    @GetMapping("/verify")
    public java.util.Map<String, Boolean> usernameTaken(@RequestParam String username) {
        return java.util.Map.of("taken", service.usernameTaken(username));
    }
}
