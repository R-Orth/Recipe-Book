package com.chefit.recipes.dao;

import com.chefit.recipes.model.Ingredient;
import com.chefit.recipes.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecipeDAOTest {

    @Mock JedisPool jedisPool;
    @Mock Jedis jedis;

    RecipeDAO dao;

    @BeforeEach
    void setup() {
        when(jedisPool.getResource()).thenReturn(jedis);
        dao = new RecipeDAO(jedisPool);
    }

    // --- save ---

    @Test
    void save_generatesUuidWhenIdBlank() {
        Recipe result = dao.save(new Recipe(null, "Soup", "30", "4", List.of(), List.of()));

        assertNotNull(result.id());
        assertFalse(result.id().isBlank());
    }

    @Test
    void save_preservesExistingId() {
        Recipe result = dao.save(new Recipe("abc-123", "Soup", "30", "4", List.of(), List.of()));

        assertEquals("abc-123", result.id());
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_writesCorrectHashFieldsAndIndex() {
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        dao.save(new Recipe("abc-123", "Pasta", "20", "2",
                List.of(new Ingredient("Flour", "2", "Cup")),
                List.of("Boil water", "Add pasta")));

        verify(jedis).hset(eq("recipe:abc-123"), mapCaptor.capture());
        verify(jedis).sadd("recipe:index", "abc-123");

        Map<String, String> fields = mapCaptor.getValue();
        assertEquals("abc-123",   fields.get("id"));
        assertEquals("Pasta",     fields.get("name"));
        assertEquals("20",        fields.get("time"));
        assertEquals("2",         fields.get("servings"));
        assertNotNull(fields.get("ingredients"));
        assertNotNull(fields.get("steps"));
        assertTrue(fields.get("ingredients").contains("Flour"));
        assertTrue(fields.get("steps").contains("Boil water"));
    }

    // --- findById ---

    @Test
    void findById_returnsEmptyWhenMissing() {
        when(jedis.hgetAll("recipe:missing")).thenReturn(Map.of());

        assertTrue(dao.findById("missing").isEmpty());
    }

    @Test
    void findById_deserializesRecipeCorrectly() {
        when(jedis.hgetAll("recipe:1")).thenReturn(Map.of(
                "id",          "1",
                "name",        "Soup",
                "time",        "30",
                "servings",    "4",
                "ingredients", "[{\"name\":\"Salt\",\"amount\":\"1\",\"measurement\":\"Tsp\"}]",
                "steps",       "[\"Boil water\",\"Add salt\"]"
        ));

        Optional<Recipe> result = dao.findById("1");

        assertTrue(result.isPresent());
        Recipe recipe = result.get();
        assertEquals("Soup", recipe.name());
        assertEquals(1, recipe.ingredients().size());
        assertEquals("Salt", recipe.ingredients().get(0).name());
        assertEquals(2, recipe.steps().size());
        assertEquals("Boil water", recipe.steps().get(0));
    }

    // --- findAll ---

    @Test
    void findAll_returnsAllRecipes() {
        when(jedis.smembers("recipe:index")).thenReturn(Set.of("1", "2"));
        when(jedis.hgetAll("recipe:1")).thenReturn(Map.of(
                "id", "1", "name", "Soup", "time", "30", "servings", "4",
                "ingredients", "[]", "steps", "[]"));
        when(jedis.hgetAll("recipe:2")).thenReturn(Map.of(
                "id", "2", "name", "Stew", "time", "60", "servings", "6",
                "ingredients", "[]", "steps", "[]"));

        List<Recipe> all = dao.findAll();

        assertEquals(2, all.size());
    }

    @Test
    void findAll_returnsEmptyListWhenIndexEmpty() {
        when(jedis.smembers("recipe:index")).thenReturn(Set.of());

        assertTrue(dao.findAll().isEmpty());
    }

    // --- delete ---

    @Test
    void delete_returnsTrueAndRemovesHashAndIndex() {
        when(jedis.del("recipe:abc-123")).thenReturn(1L);

        boolean result = dao.delete("abc-123");

        assertTrue(result);
        verify(jedis).del("recipe:abc-123");
        verify(jedis).srem("recipe:index", "abc-123");
    }

    @Test
    void delete_returnsFalseWhenIdNotFound() {
        when(jedis.del("recipe:missing")).thenReturn(0L);

        assertFalse(dao.delete("missing"));
        verify(jedis).srem("recipe:index", "missing");
    }
}
