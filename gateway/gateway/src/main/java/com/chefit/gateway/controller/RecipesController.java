package com.chefit.gateway.controller;

import com.chefit.gateway.dto.Ingredient;
import com.chefit.gateway.dto.Recipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/items")
public class RecipesController {

    private static final Logger log = LoggerFactory.getLogger(RecipesController.class);

    @GetMapping
    public Mono<List<Recipe>> listRecipes() {
        log.info("[STUB] GET /items — list recipes");
        return Mono.just(List.of(
            new Recipe(
                "stub-1",
                "Gateway Stub Recipe",
                "5",
                "1",
                List.of(new Ingredient("placeholder", "1", "Pinch")),
                List.of("This is a stub response from the Spring gateway.")
            )
        ));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Recipe>> getRecipe(@PathVariable String id) {
        log.info("[STUB] GET /items/{} — fetch recipe by id", id);
        Recipe stub = new Recipe(
            id,
            "Gateway Stub Recipe " + id,
            "5",
            "1",
            List.of(new Ingredient("placeholder", "1", "Pinch")),
            List.of("This is a stub response from the Spring gateway for id=" + id + ".")
        );
        return Mono.just(ResponseEntity.ok(stub));
    }

    @PutMapping
    public Mono<ResponseEntity<Recipe>> createRecipe(@RequestBody Recipe recipe) {
        log.info("[STUB] PUT /items — create recipe id={} name={}", recipe.id(), recipe.name());
        log.debug("[STUB] PUT /items payload: {}", recipe);
        return Mono.just(ResponseEntity.ok(recipe));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteRecipe(@PathVariable String id) {
        log.info("[STUB] DELETE /items/{} — delete recipe", id);
        return Mono.just(ResponseEntity.noContent().build());
    }
}
