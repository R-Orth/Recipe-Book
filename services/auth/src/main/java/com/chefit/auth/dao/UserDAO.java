package com.chefit.auth.dao;

import com.chefit.auth.model.User;
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
import java.util.UUID;

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

    // Atomically claims the identifier index with SETNX; rejects with IdentifierTakenException
    // if the index already points to a different uuid. Preserves uuid + createDate when
    // present so replication replay (Task 10) is idempotent.
    public User save(User user) {
        String uuid = (user.uuid() == null || user.uuid().isBlank())
                ? UUID.randomUUID().toString()
                : user.uuid();
        String now = Instant.now().toString();
        String createDate = (user.createDate() == null || user.createDate().isBlank())
                ? now : user.createDate();

        User toSave = new User(uuid, user.identifier(), user.identifierIsEmail(),
                user.hashedPassword(), user.googleSub(), providersOrEmpty(user.providers()),
                user.realname(), user.ip(), createDate, now);

        try (Jedis jedis = jedisPool.getResource()) {
            // Atomic claim — SETNX returns 1 if the key didn't exist, 0 if it did.
            long claimed = jedis.setnx(IDENTIFIER_INDEX + toSave.identifier(), uuid);
            boolean claimedThisCall = claimed == 1L;
            if (!claimedThisCall) {
                String existing = jedis.get(IDENTIFIER_INDEX + toSave.identifier());
                if (!uuid.equals(existing)) {
                    throw new IdentifierTakenException(toSave.identifier());
                }
                // Same uuid — replication replay; proceed to overwrite the record idempotently.
            }

            // If any post-claim write fails, roll back the just-claimed identifier index so
            // we don't leave an orphan pointing to a uuid with no user hash. We only roll
            // back the claim WE made — on a replay, the index was already there before us.
            try {
                jedis.hset(KEY_PREFIX + uuid, serialize(toSave));
                jedis.sadd(INDEX_KEY, uuid);
                if (notBlank(toSave.googleSub())) jedis.set(GOOGLE_INDEX + toSave.googleSub(), uuid);
            } catch (Exception inner) {
                if (claimedThisCall) {
                    try {
                        jedis.del(IDENTIFIER_INDEX + toSave.identifier());
                    } catch (Exception cleanup) {
                        log.error("Rollback failed; orphan index left at idx:identifier:{}",
                                toSave.identifier(), cleanup);
                    }
                }
                throw inner;
            }
            log.debug("Saved user uuid={} identifier={}", uuid, toSave.identifier());
        } catch (IdentifierTakenException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to save user uuid=" + uuid, e);
        }

        return toSave;
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

    public Optional<User> findByIdentifier(String identifier) {
        return findByIndex(IDENTIFIER_INDEX + identifier);
    }

    public Optional<User> findByGoogleSub(String sub) {
        return findByIndex(GOOGLE_INDEX + sub);
    }

    // Links a Google identity onto an existing account. Refuses loudly if the account
    // already has a googleSub — no story for switching Google accounts in Task 5; better
    // to fail than silently overwrite.
    public User linkGoogle(String uuid, String googleSub) {
        User existing = findById(uuid)
                .orElseThrow(() -> new IllegalStateException("Cannot link Google to missing user uuid=" + uuid));
        if (notBlank(existing.googleSub())) {
            throw new IllegalStateException("User uuid=" + uuid + " already has a linked Google account");
        }

        List<String> providers = new ArrayList<>(providersOrEmpty(existing.providers()));
        if (!providers.contains("google")) providers.add("google");

        User updated = new User(existing.uuid(), existing.identifier(), existing.identifierIsEmail(),
                existing.hashedPassword(), googleSub, providers,
                existing.realname(), existing.ip(), existing.createDate(), Instant.now().toString());

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(KEY_PREFIX + uuid, serialize(updated));
            jedis.set(GOOGLE_INDEX + googleSub, uuid);
            log.debug("Linked google sub to user uuid={}", uuid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to link Google to user uuid=" + uuid, e);
        }

        return updated;
    }

    // Links a local password onto an existing account. Refuses loudly if the account already
    // has a hashedPassword — no password-change flow in Task 5; better to fail than overwrite.
    public User linkLocal(String uuid, String hashedPassword) {
        User existing = findById(uuid)
                .orElseThrow(() -> new IllegalStateException("Cannot link local password to missing user uuid=" + uuid));
        if (notBlank(existing.hashedPassword())) {
            throw new IllegalStateException("User uuid=" + uuid + " already has a local password set");
        }

        List<String> providers = new ArrayList<>(providersOrEmpty(existing.providers()));
        if (!providers.contains("local")) providers.add("local");

        User updated = new User(existing.uuid(), existing.identifier(), existing.identifierIsEmail(),
                hashedPassword, existing.googleSub(), providers,
                existing.realname(), existing.ip(), existing.createDate(), Instant.now().toString());

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(KEY_PREFIX + uuid, serialize(updated));
            log.debug("Linked local password to user uuid={}", uuid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to link local password to user uuid=" + uuid, e);
        }

        return updated;
    }

    private Optional<User> findByIndex(String indexKey) {
        try (Jedis jedis = jedisPool.getResource()) {
            String uuid = jedis.get(indexKey);
            if (uuid == null || uuid.isBlank()) return Optional.empty();
            return findById(uuid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to resolve index " + indexKey, e);
        }
    }

    private Map<String, String> serialize(User user) throws Exception {
        Map<String, String> fields = new HashMap<>();
        fields.put("uuid",              user.uuid());
        fields.put("identifier",        nullToEmpty(user.identifier()));
        fields.put("identifierIsEmail", String.valueOf(user.identifierIsEmail()));
        fields.put("hashedPassword",    nullToEmpty(user.hashedPassword()));
        fields.put("googleSub",         nullToEmpty(user.googleSub()));
        fields.put("providers",         objectMapper.writeValueAsString(providersOrEmpty(user.providers())));
        fields.put("realname",          nullToEmpty(user.realname()));
        fields.put("ip",                nullToEmpty(user.ip()));
        fields.put("createDate",        nullToEmpty(user.createDate()));
        fields.put("modifyDate",        nullToEmpty(user.modifyDate()));
        return fields;
    }

    private User deserialize(Map<String, String> fields) throws Exception {
        List<String> providers = objectMapper.readValue(
                fields.getOrDefault("providers", "[]"), new TypeReference<>() {});
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

    private static List<String> providersOrEmpty(List<String> providers) {
        return providers == null ? List.of() : providers;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
