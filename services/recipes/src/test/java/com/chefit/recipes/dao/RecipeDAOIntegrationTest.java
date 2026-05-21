package com.chefit.recipes.dao;

import com.chefit.recipes.model.Ingredient;
import com.chefit.recipes.model.Recipe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeDAOIntegrationTest {

    static JedisPool jedisPool;
    RecipeDAO dao;

    @BeforeAll
    static void startPool() {
        jedisPool = new JedisPool("localhost", 6379);
    }

    @AfterAll
    static void closePool() {
        jedisPool.close();
    }

    @BeforeEach
    void cleanRedisAndBuildDao() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys("recipe:*");
            if (!keys.isEmpty()) jedis.del(keys.toArray(new String[0]));
            jedis.del("recipe:index");
        }
        dao = new RecipeDAO(jedisPool);
    }

    // --- save + findById round-trip ---

    @Test
    void save_andFindById_roundTrip() {
        Recipe saved = dao.save(new Recipe(null, "Pasta", "20", "2", List.of(), List.of("Boil pasta")));

        Recipe found = dao.findById(saved.id()).orElseThrow();

        assertEquals(saved.id(), found.id());
        assertEquals("Pasta",      found.name());
        assertEquals("20",         found.time());
        assertEquals("2",          found.servings());
        assertEquals(1,            found.steps().size());
        assertEquals("Boil pasta", found.steps().get(0));
    }

    @Test
    void save_withIngredients_deserializesCorrectly() {
        List<Ingredient> ingredients = List.of(
                new Ingredient("Flour",  "2", "Cup"),
                new Ingredient("Salt",   "1", "Tsp"),
                new Ingredient("Butter", "50", "g")
        );
        List<String> steps = List.of("Mix dry ingredients", "Add butter", "Bake at 350F");

        Recipe saved = dao.save(new Recipe(null, "Bread", "60", "8", ingredients, steps));
        Recipe found = dao.findById(saved.id()).orElseThrow();

        assertEquals(3,        found.ingredients().size());
        assertEquals("Flour",  found.ingredients().get(0).name());
        assertEquals("Cup",    found.ingredients().get(0).measurement());
        assertEquals("Salt",   found.ingredients().get(1).name());
        assertEquals("Butter", found.ingredients().get(2).name());
        assertEquals(3,        found.steps().size());
        assertEquals("Bake at 350F", found.steps().get(2));
    }

    // --- findAll ---

    @Test
    void findAll_returnsAllSavedRecipes() {
        dao.save(new Recipe(null, "Soup",  "30", "4", List.of(), List.of()));
        dao.save(new Recipe(null, "Stew",  "60", "6", List.of(), List.of()));
        dao.save(new Recipe(null, "Salad", "10", "2", List.of(), List.of()));

        assertEquals(3, dao.findAll().size());
    }

    @Test
    void findAll_returnsEmptyListOnFreshRedis() {
        assertTrue(dao.findAll().isEmpty());
    }

    // --- delete ---

    @Test
    void delete_removesHashAndIndex() {
        Recipe saved = dao.save(new Recipe(null, "Toast", "5", "1", List.of(), List.of("Toast bread")));

        boolean deleted = dao.delete(saved.id());

        assertTrue(deleted);
        assertTrue(dao.findById(saved.id()).isEmpty());
        assertTrue(dao.findAll().stream().noneMatch(r -> r.id().equals(saved.id())));
    }

    @Test
    void delete_returnsFalseForNonExistentId() {
        assertFalse(dao.delete("does-not-exist"));
    }

    @Test
    void delete_doesNotAffectOtherRecipes() {
        Recipe a = dao.save(new Recipe(null, "Soup",  "30", "4", List.of(), List.of()));
        Recipe b = dao.save(new Recipe(null, "Stew",  "60", "6", List.of(), List.of()));

        dao.delete(a.id());

        List<Recipe> remaining = dao.findAll();
        assertEquals(1, remaining.size());
        assertEquals(b.id(), remaining.get(0).id());
    }

    // --- idempotent replay (replication) ---

    @Test
    void save_withExistingId_overwritesForReplication() {
        String fixedId = "replication-replay-id";
        dao.save(new Recipe(fixedId, "Soup",         "30", "4", List.of(), List.of()));
        dao.save(new Recipe(fixedId, "Soup Updated", "35", "4", List.of(), List.of()));

        List<Recipe> all = dao.findAll();
        assertEquals(1, all.size());
        assertEquals("Soup Updated", dao.findById(fixedId).orElseThrow().name());
        assertEquals("35",           dao.findById(fixedId).orElseThrow().time());
    }
}
