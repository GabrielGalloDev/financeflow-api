package com.gabriel.financeflow.category.controller;

import com.gabriel.financeflow.category.dto.CategoryResponse;
import com.gabriel.financeflow.category.dto.CreateCategoryRequest;
import com.gabriel.financeflow.category.dto.UpdateCategoryRequest;
import com.gabriel.financeflow.category.entity.CategoryType;
import com.gabriel.financeflow.category.service.CategoryService;
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
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories", description = "Categorias padrão do sistema e personalizadas do usuário")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "Lista as categorias padrão e as do usuário (filtros opcionais: tipo e arquivadas)")
    @GetMapping
    public List<CategoryResponse> list(
            @RequestParam(required = false) CategoryType type,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return categoryService.list(type, includeArchived);
    }

    @Operation(summary = "Cria uma categoria personalizada")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = categoryService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Busca uma categoria (padrão ou do usuário)")
    @GetMapping("/{id}")
    public CategoryResponse get(@PathVariable UUID id) {
        return categoryService.get(id);
    }

    @Operation(summary = "Renomeia uma categoria do usuário (o tipo não muda)")
    @PutMapping("/{id}")
    public CategoryResponse rename(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.rename(id, request);
    }

    @Operation(summary = "Arquiva uma categoria do usuário")
    @PatchMapping("/{id}/archive")
    public CategoryResponse archive(@PathVariable UUID id) {
        return categoryService.archive(id);
    }

    @Operation(summary = "Desarquiva uma categoria do usuário")
    @PatchMapping("/{id}/unarchive")
    public CategoryResponse unarchive(@PathVariable UUID id) {
        return categoryService.unarchive(id);
    }
}
