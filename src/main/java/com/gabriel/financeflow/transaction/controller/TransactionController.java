package com.gabriel.financeflow.transaction.controller;

import com.gabriel.financeflow.shared.dto.PageResponse;
import com.gabriel.financeflow.transaction.dto.TransactionFilter;
import com.gabriel.financeflow.transaction.dto.TransactionRequest;
import com.gabriel.financeflow.transaction.dto.TransactionResponse;
import com.gabriel.financeflow.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Receitas e despesas do usuário autenticado")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Cria uma receita ou despesa")
    @PostMapping
    public ResponseEntity<TransactionResponse> create(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse created = transactionService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Busca as movimentações, com filtros opcionais, paginação e ordenação")
    @GetMapping
    public PageResponse<TransactionResponse> search(
            @Valid @ParameterObject TransactionFilter filter,
            @ParameterObject @PageableDefault(sort = "date", direction = Sort.Direction.DESC) Pageable pageable) {
        return transactionService.search(filter, pageable);
    }

    @Operation(summary = "Busca uma movimentação")
    @GetMapping("/{id}")
    public TransactionResponse get(@PathVariable UUID id) {
        return transactionService.get(id);
    }

    @Operation(summary = "Edita uma receita ou despesa (transferências não podem ser editadas)")
    @PutMapping("/{id}")
    public TransactionResponse update(@PathVariable UUID id, @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(id, request);
    }

    @Operation(summary = "Exclui a movimentação (exclusão lógica; numa transferência, as duas pernas)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        transactionService.delete(id);
    }

    @Operation(summary = "Marca como paga (se paga antes do vencimento, a data vira hoje)")
    @PatchMapping("/{id}/pay")
    public TransactionResponse pay(@PathVariable UUID id) {
        return transactionService.pay(id);
    }
}
