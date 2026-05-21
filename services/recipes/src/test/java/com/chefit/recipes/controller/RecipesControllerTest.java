package com.chefit.recipes.controller;

import com.chefit.recipes.dao.RecipeDAO;
import com.chefit.recipes.model.Ingredient;
import com.chefit.recipes.model.Recipe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RecipesController.class)
class RecipesControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean RecipeDAO recipeDAO;

    // --- GET /items ---

    @Test
    void listRecipes_returns200WithRecipeList() throws Exception {
        when(recipeDAO.findAll()).thenReturn(List.of(
                new Recipe("1", "Soup", "30", "4", List.of(), List.of("Boil water"))
        ));

        mockMvc.perform(get("/items"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$[0].id").value("1"))
               .andExpect(jsonPath("$[0].name").value("Soup"))
               .andExpect(jsonPath("$[0].steps[0]").value("Boil water"));
    }

    @Test
    void listRecipes_returns200WithEmptyList() throws Exception {
        when(recipeDAO.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/items"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$").isArray())
               .andExpect(jsonPath("$").isEmpty());
    }

    // --- GET /items/{id} ---

    @Test
    void getRecipe_returns200WhenFound() throws Exception {
        Recipe recipe = new Recipe("1", "Soup", "30", "4",
                List.of(new Ingredient("Salt", "1", "Tsp")),
                List.of("Boil water"));
        when(recipeDAO.findById("1")).thenReturn(Optional.of(recipe));

        mockMvc.perform(get("/items/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value("1"))
               .andExpect(jsonPath("$.name").value("Soup"))
               .andExpect(jsonPath("$.ingredients[0].name").value("Salt"));
    }

    @Test
    void getRecipe_returns404WhenNotFound() throws Exception {
        when(recipeDAO.findById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/items/missing"))
               .andExpect(status().isNotFound());
    }

    // --- PUT /items ---

    @Test
    void saveRecipe_returns200WithSavedRecipe() throws Exception {
        Recipe saved = new Recipe("uuid-1", "Pasta", "20", "2",
                List.of(new Ingredient("Flour", "2", "Cup")),
                List.of("Boil water", "Add pasta"));
        when(recipeDAO.save(any())).thenReturn(saved);

        mockMvc.perform(put("/items")
                   .contentType(MediaType.APPLICATION_JSON)
                   .content("""
                       {
                         "name": "Pasta",
                         "time": "20",
                         "servings": "2",
                         "ingredients": [{"name":"Flour","amount":"2","measurement":"Cup"}],
                         "steps": ["Boil water", "Add pasta"]
                       }
                   """))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value("uuid-1"))
               .andExpect(jsonPath("$.name").value("Pasta"))
               .andExpect(jsonPath("$.ingredients[0].name").value("Flour"))
               .andExpect(jsonPath("$.steps[1]").value("Add pasta"));
    }

    // --- DELETE /items/{id} ---

    @Test
    void deleteRecipe_returns204AndCallsDao() throws Exception {
        mockMvc.perform(delete("/items/abc-123"))
               .andExpect(status().isNoContent());

        verify(recipeDAO).delete("abc-123");
    }
}
