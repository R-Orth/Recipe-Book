package com.chefit.recipes.dao;

import com.chefit.recipes.model.Ingredient;
import com.chefit.recipes.model.Recipe;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class RecipeDAO {

    private static final Logger log = LoggerFactory.getLogger(RecipeDAO.class);

    static final String INDEX_KEY = "recipe:index";
    static final String KEY_PREFIX = "recipe:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RecipeDAO(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    // Assigns a new UUID if the recipe has no id — new saves from the controller.
    // Preserves the provided id if present — enables idempotent replay on replicas
    // when the ReplicationManager rebroadcasts this operation (Task 10).
    public Recipe save(Recipe recipe) {
        String id = (recipe.id() == null || recipe.id().isBlank())
                ? UUID.randomUUID().toString()
                : recipe.id();

        Recipe toSave = new Recipe(id, recipe.name(), recipe.time(),
                recipe.servings(), recipe.ingredients(), recipe.steps());

        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> fields = new HashMap<>();
            fields.put("id",          id);
            fields.put("name",        toSave.name());
            fields.put("time",        toSave.time());
            fields.put("servings",    toSave.servings());
            fields.put("ingredients", objectMapper.writeValueAsString(toSave.ingredients()));
            fields.put("steps",       objectMapper.writeValueAsString(toSave.steps()));

            jedis.hset(KEY_PREFIX + id, fields);
            jedis.sadd(INDEX_KEY, id);
            log.debug("Saved recipe id={} name={}", id, toSave.name());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save recipe id=" + id, e);
        }

        return toSave;
    }

    public Optional<Recipe> findById(String id) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> fields = jedis.hgetAll(KEY_PREFIX + id);
            if (fields == null || fields.isEmpty()) return Optional.empty();
            return Optional.of(deserialize(fields));
        } catch (Exception e) {
            throw new RuntimeException("Failed to find recipe id=" + id, e);
        }
    }

    public List<Recipe> findAll() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> ids = jedis.smembers(INDEX_KEY);
            List<Recipe> recipes = new ArrayList<>(ids.size());
            for (String id : ids) {
                Map<String, String> fields = jedis.hgetAll(KEY_PREFIX + id);
                if (fields != null && !fields.isEmpty()) {
                    recipes.add(deserialize(fields));
                }
            }
            log.debug("findAll returned {} recipes", recipes.size());
            return recipes;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list recipes", e);
        }
    }

    public boolean delete(String id) {
        try (Jedis jedis = jedisPool.getResource()) {
            long removed = jedis.del(KEY_PREFIX + id);
            jedis.srem(INDEX_KEY, id);
            log.debug("Deleted recipe id={} (existed={})", id, removed > 0);
            return removed > 0;
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete recipe id=" + id, e);
        }
    }

    private Recipe deserialize(Map<String, String> fields) throws Exception {
        List<Ingredient> ingredients = objectMapper.readValue(
                fields.get("ingredients"), new TypeReference<>() {});
        List<String> steps = objectMapper.readValue(
                fields.get("steps"), new TypeReference<>() {});
        return new Recipe(
                fields.get("id"),
                fields.get("name"),
                fields.get("time"),
                fields.get("servings"),
                ingredients,
                steps);
    }
}
