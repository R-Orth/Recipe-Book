package com.chefit.recipes.controller;

import com.chefit.recipes.dao.RecipeDAO;
import com.chefit.recipes.model.Recipe;
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

import java.util.List;

@RestController
@RequestMapping("/items")
public class RecipesController {

    private static final Logger log = LoggerFactory.getLogger(RecipesController.class);

    private final RecipeDAO recipeDAO;

    public RecipesController(RecipeDAO recipeDAO) {
        this.recipeDAO = recipeDAO;
    }

    @GetMapping
    public List<Recipe> listRecipes() {
        log.info("GET /items");
        return recipeDAO.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Recipe> getRecipe(@PathVariable String id) {
        log.info("GET /items/{}", id);
        return recipeDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    public ResponseEntity<Recipe> saveRecipe(@RequestBody Recipe recipe) {
        log.info("PUT /items name={}", recipe.name());
        Recipe saved = recipeDAO.save(recipe);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecipe(@PathVariable String id) {
        log.info("DELETE /items/{}", id);
        recipeDAO.delete(id);
        return ResponseEntity.noContent().build();
    }
}
