package com.gabriel.financeflow.account.controller;

import com.gabriel.financeflow.account.dto.AccountRequest;
import com.gabriel.financeflow.account.dto.AccountResponse;
import com.gabriel.financeflow.account.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts", description = "Contas financeiras do usuário autenticado")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Cria uma conta")
    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody AccountRequest request) {
        AccountResponse created = accountService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Lista as contas (as arquivadas só com includeArchived=true)")
    @GetMapping
    public List<AccountResponse> list(@RequestParam(defaultValue = "false") boolean includeArchived) {
        return accountService.list(includeArchived);
    }

    @Operation(summary = "Busca uma conta")
    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @Operation(summary = "Edita nome, tipo, instituição e saldo inicial")
    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return accountService.update(id, request);
    }

    @Operation(summary = "Arquiva a conta (sem apagar o histórico)")
    @PatchMapping("/{id}/archive")
    public AccountResponse archive(@PathVariable UUID id) {
        return accountService.archive(id);
    }

    @Operation(summary = "Desarquiva a conta")
    @PatchMapping("/{id}/unarchive")
    public AccountResponse unarchive(@PathVariable UUID id) {
        return accountService.unarchive(id);
    }
}
