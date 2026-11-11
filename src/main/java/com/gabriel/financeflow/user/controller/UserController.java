package com.gabriel.financeflow.user.controller;

import com.gabriel.financeflow.user.dto.UpdateUserRequest;
import com.gabriel.financeflow.user.dto.UserResponse;
import com.gabriel.financeflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Perfil do usuário autenticado")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Dados do usuário autenticado")
    @GetMapping("/me")
    public UserResponse me() {
        return userService.getCurrentUser();
    }

    @Operation(summary = "Atualiza nome, e-mail, moeda e fuso horário")
    @PutMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return userService.updateCurrentUser(request);
    }
}
