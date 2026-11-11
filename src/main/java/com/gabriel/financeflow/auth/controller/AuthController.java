package com.gabriel.financeflow.auth.controller;

import com.gabriel.financeflow.auth.dto.LoginRequest;
import com.gabriel.financeflow.auth.dto.RefreshRequest;
import com.gabriel.financeflow.auth.dto.RegisterRequest;
import com.gabriel.financeflow.auth.dto.TokenResponse;
import com.gabriel.financeflow.auth.service.AuthService;
import com.gabriel.financeflow.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Cadastro, login, renovação de token e logout")
// Lista vazia: sobrescreve a exigência global de token do OpenApiConfig.
// Sem isso, o Swagger mandaria o token (às vezes vencido) também aqui, e o Spring Security responderia 401.
@SecurityRequirements()
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Cadastra um novo usuário")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "Autentica e devolve o access token e o refresh token")
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Troca um refresh token por um par novo (o antigo é revogado)")
    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "Revoga o refresh token")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
    }
}
