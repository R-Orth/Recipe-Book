package com.chefit.users.dao;

import com.chefit.users.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Read + management access to the same Redis user records the auth service writes.
// Key layout (shared with auth): user:{uuid} hash, user:index set, idx:identifier:{id}
// and idx:google:{sub} indexes. This DAO never creates accounts — registration lives in
// auth — it only lists, reads, partially updates, and deletes existing records.
@Component
public class UserDAO {

    private static final Logger log = LoggerFactory.getLogger(UserDAO.class);

    static final String KEY_PREFIX = "user:";
    static final String INDEX_KEY = "user:index";
    static final String IDENTIFIER_INDEX = "idx:identifier:";
    static final String GOOGLE_INDEX = "idx:google:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserDAO(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public List<User> findAll() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> uuids = jedis.smembers(INDEX_KEY);
            List<User> users = new ArrayList<>();
            if (uuids == null) return users;
            for (String uuid : uuids) {
                Map<String, String> fields = jedis.hgetAll(KEY_PREFIX + uuid);
                if (fields == null || fields.isEmpty()) continue; // orphaned index entry
                users.add(deserialize(fields));
            }
            return users;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list users", e);
        }
    }

    public Optional<User> findById(String uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> fields = jedis.hgetAll(KEY_PREFIX + uuid);
            if (fields == null || fields.isEmpty()) return Optional.empty();
            return Optional.of(deserialize(fields));
        } catch (Exception e) {
            throw new RuntimeException("Failed to find user uuid=" + uuid, e);
        }
    }

    // Applies non-null fields from req to the stored user and writes back.
    // If req.identifier() is non-null, atomically releases the old identifier
    // index and claims the new one; throws IdentifierTakenException if the new
    // identifier is already claimed by a different uuid. modifyDate is refreshed
    // on every write; an empty request is a no-op write and just returns current state.
    public User update(String uuid, UpdateRequest req) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> current = jedis.hgetAll(KEY_PREFIX + uuid);
            if (current == null || current.isEmpty()) {
                throw new IllegalStateException("Cannot update missing user uuid=" + uuid);
            }
            User existing = deserialize(current);

            boolean identifierChange = req.identifier() != null;
            boolean realnameChange = req.realname() != null;
            boolean passwordChange = req.newHashedPassword() != null;

            if (!identifierChange && !realnameChange && !passwordChange) {
                return existing; // nothing to write
            }

            Map<String, String> changes = new HashMap<>();
            if (realnameChange) changes.put("realname", req.realname());
            if (passwordChange) changes.put("hashedPassword", req.newHashedPassword());

            boolean newIsEmail = existing.identifierIsEmail();
            if (identifierChange) {
                newIsEmail = User.looksLikeEmail(req.identifier());
                changes.put("identifier", req.identifier());
                changes.put("identifierIsEmail", String.valueOf(newIsEmail));
                // Atomically claim the new identifier before touching anything else.
                long claimed = jedis.setnx(IDENTIFIER_INDEX + req.identifier(), uuid);
                if (claimed != 1L) {
                    throw new IdentifierTakenException(req.identifier());
                }
            }

            String now = Instant.now().toString();
            changes.put("modifyDate", now);

            try {
                jedis.hset(KEY_PREFIX + uuid, changes);
            } catch (RuntimeException writeFailure) {
                // Roll back the claim we just made; leave the old index intact.
                if (identifierChange) {
                    try {
                        jedis.del(IDENTIFIER_INDEX + req.identifier());
                    } catch (Exception cleanup) {
                        log.error("Rollback failed; orphan index left at idx:identifier:{}",
                                req.identifier(), cleanup);
                    }
                }
                throw writeFailure;
            }

            // Release the old identifier index only after the write succeeds.
            if (identifierChange && !req.identifier().equals(existing.identifier())) {
                jedis.del(IDENTIFIER_INDEX + existing.identifier());
            }

            log.debug("Updated user uuid={}", uuid);
            return new User(
                    existing.uuid(),
                    identifierChange ? req.identifier() : existing.identifier(),
                    newIsEmail,
                    passwordChange ? req.newHashedPassword() : existing.hashedPassword(),
                    existing.googleSub(),
                    existing.providers(),
                    realnameChange ? req.realname() : existing.realname(),
                    existing.ip(),
                    existing.createDate(),
                    now);
        } catch (IdentifierTakenException e) {
            throw e;
        }
    }

    // Removes the user hash, both identifier indexes, and the uuid from the index set.
    public void delete(String uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> fields = jedis.hgetAll(KEY_PREFIX + uuid);
            jedis.del(KEY_PREFIX + uuid);
            if (fields != null && !fields.isEmpty()) {
                String identifier = fields.get("identifier");
                if (identifier != null && !identifier.isEmpty()) {
                    jedis.del(IDENTIFIER_INDEX + identifier);
                }
                String googleSub = fields.get("googleSub");
                if (googleSub != null && !googleSub.isEmpty()) {
                    jedis.del(GOOGLE_INDEX + googleSub);
                }
            }
            jedis.srem(INDEX_KEY, uuid);
            log.debug("Deleted user uuid={}", uuid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete user uuid=" + uuid, e);
        }
    }

    private User deserialize(Map<String, String> fields) {
        List<String> providers;
        try {
            providers = objectMapper.readValue(
                    fields.getOrDefault("providers", "[]"), new TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse providers for user", e);
        }
        return new User(
                fields.get("uuid"),
                emptyToNull(fields.get("identifier")),
                Boolean.parseBoolean(fields.get("identifierIsEmail")),
                emptyToNull(fields.get("hashedPassword")),
                emptyToNull(fields.get("googleSub")),
                providers,
                emptyToNull(fields.get("realname")),
                emptyToNull(fields.get("ip")),
                emptyToNull(fields.get("createDate")),
                emptyToNull(fields.get("modifyDate")));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
