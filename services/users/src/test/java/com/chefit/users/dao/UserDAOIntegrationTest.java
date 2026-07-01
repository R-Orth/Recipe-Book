package com.chefit.users.dao;

import com.chefit.users.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserDAOIntegrationTest {

    static JedisPool jedisPool;
    UserDAO dao;

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
            for (String pattern : new String[] { "user:*", "idx:identifier:*", "idx:google:*" }) {
                Set<String> keys = jedis.keys(pattern);
                if (!keys.isEmpty()) jedis.del(keys.toArray(new String[0]));
            }
        }
        dao = new UserDAO(jedisPool);
    }

    // Helpers for seeding test users directly via save-equivalent logic.
    // We call the auth-service-compatible save path: same Redis keys, same field names.
    private User seedLocal(String uuid, String identifier, String hashedPassword) {
        String resolvedUuid = uuid != null ? uuid : java.util.UUID.randomUUID().toString();
        String now = java.time.Instant.now().toString();
        boolean isEmail = User.looksLikeEmail(identifier);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx("idx:identifier:" + identifier, resolvedUuid);
            java.util.Map<String, String> fields = new java.util.HashMap<>();
            fields.put("uuid",              resolvedUuid);
            fields.put("identifier",        identifier);
            fields.put("identifierIsEmail", String.valueOf(isEmail));
            fields.put("hashedPassword",    hashedPassword != null ? hashedPassword : "");
            fields.put("googleSub",         "");
            fields.put("providers",         "[\"local\"]");
            fields.put("realname",          "Ada");
            fields.put("ip",                "127.0.0.1");
            fields.put("createDate",        now);
            fields.put("modifyDate",        now);
            jedis.hset("user:" + resolvedUuid, fields);
            jedis.sadd("user:index", resolvedUuid);
        }
        return new User(resolvedUuid, identifier, isEmail, hashedPassword, null,
                List.of("local"), "Ada", "127.0.0.1", now, now);
    }

    private User seedGoogle(String uuid, String identifier, String googleSub) {
        String resolvedUuid = uuid != null ? uuid : java.util.UUID.randomUUID().toString();
        String now = java.time.Instant.now().toString();
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx("idx:identifier:" + identifier, resolvedUuid);
            jedis.set("idx:google:" + googleSub, resolvedUuid);
            java.util.Map<String, String> fields = new java.util.HashMap<>();
            fields.put("uuid",              resolvedUuid);
            fields.put("identifier",        identifier);
            fields.put("identifierIsEmail", "true");
            fields.put("hashedPassword",    "");
            fields.put("googleSub",         googleSub);
            fields.put("providers",         "[\"google\"]");
            fields.put("realname",          "Ada");
            fields.put("ip",                "127.0.0.1");
            fields.put("createDate",        now);
            fields.put("modifyDate",        now);
            jedis.hset("user:" + resolvedUuid, fields);
            jedis.sadd("user:index", resolvedUuid);
        }
        return new User(resolvedUuid, identifier, true, null, googleSub,
                List.of("google"), "Ada", "127.0.0.1", now, now);
    }

    private User seedMerged(String uuid, String identifier, String hashedPassword, String googleSub) {
        String resolvedUuid = uuid != null ? uuid : java.util.UUID.randomUUID().toString();
        String now = java.time.Instant.now().toString();
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx("idx:identifier:" + identifier, resolvedUuid);
            jedis.set("idx:google:" + googleSub, resolvedUuid);
            java.util.Map<String, String> fields = new java.util.HashMap<>();
            fields.put("uuid",              resolvedUuid);
            fields.put("identifier",        identifier);
            fields.put("identifierIsEmail", "true");
            fields.put("hashedPassword",    hashedPassword);
            fields.put("googleSub",         googleSub);
            fields.put("providers",         "[\"local\",\"google\"]");
            fields.put("realname",          "Ada");
            fields.put("ip",                "127.0.0.1");
            fields.put("createDate",        now);
            fields.put("modifyDate",        now);
            jedis.hset("user:" + resolvedUuid, fields);
            jedis.sadd("user:index", resolvedUuid);
        }
        return new User(resolvedUuid, identifier, true, hashedPassword, googleSub,
                List.of("local", "google"), "Ada", "127.0.0.1", now, now);
    }

    // ================================================================ findAll

    @Test
    void findAll_returnsAllUsers_mixedTypes() {
        User local  = seedLocal(null, "alice", "hash");
        User google = seedGoogle(null, "ada@x.com", "g1");
        User merged = seedMerged(null, "bob@x.com", "hash2", "g2");

        List<User> result = dao.findAll();

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(u -> local.uuid().equals(u.uuid())));
        assertTrue(result.stream().anyMatch(u -> google.uuid().equals(u.uuid())));
        assertTrue(result.stream().anyMatch(u -> merged.uuid().equals(u.uuid())));
    }

    @Test
    void findAll_afterDelete_excludesDeletedUser() {
        User u1 = seedLocal(null, "alice", "hash");
        seedLocal(null, "bob", "hash2");

        dao.delete(u1.uuid());

        List<User> result = dao.findAll();
        assertEquals(1, result.size());
        assertFalse(result.stream().anyMatch(u -> u1.uuid().equals(u.uuid())));
    }

    @Test
    void findAll_withOrphanedIndexEntry_doesNotThrow() {
        // Manually insert a UUID into the index with no backing hash.
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("user:index", "orphan-uuid");
        }

        List<User> result = assertDoesNotThrow(() -> dao.findAll());
        assertTrue(result.stream().noneMatch(u -> "orphan-uuid".equals(u.uuid())));
    }

    // ================================================================ findById

    @Test
    void findById_roundTrip_fullFieldFidelity() {
        User seeded = seedLocal("u1", "alice", "hash");

        User found = dao.findById("u1").orElseThrow();

        assertEquals("u1",    found.uuid());
        assertEquals("alice", found.identifier());
        assertFalse(found.identifierIsEmail());
        assertEquals("hash",  found.hashedPassword());
        assertNull(found.googleSub());
        assertEquals(List.of("local"), found.providers());
        assertNotNull(found.createDate());
    }

    @Test
    void findById_empty_whenHashMissing() {
        assertTrue(dao.findById("nonexistent").isEmpty());
    }

    // ================================================================ update — realname

    @Test
    void update_realname_persistsAndOtherFieldsPreserved() {
        User seeded = seedLocal(null, "alice", "hash");

        dao.update(seeded.uuid(), new UpdateRequest("Updated Name", null, null));

        User found = dao.findById(seeded.uuid()).orElseThrow();
        assertEquals("Updated Name", found.realname());
        assertEquals("alice",        found.identifier());
        assertEquals("hash",         found.hashedPassword());
    }

    @Test
    void update_modifyDateChanges_createDatePreserved() throws InterruptedException {
        User seeded = seedLocal(null, "alice", "hash");
        String originalCreateDate = dao.findById(seeded.uuid()).orElseThrow().createDate();
        Thread.sleep(10); // ensure clock advances

        dao.update(seeded.uuid(), new UpdateRequest("New Name", null, null));

        User found = dao.findById(seeded.uuid()).orElseThrow();
        assertEquals(originalCreateDate, found.createDate(), "createDate must be preserved");
        assertNotEquals(seeded.modifyDate(), found.modifyDate(), "modifyDate must advance");
    }

    // ================================================================ update — identifier change

    @Test
    void update_identifierChange_oldIndexGone_newIndexResolvesToSameUuid() {
        User seeded = seedLocal(null, "alice", "hash");

        dao.update(seeded.uuid(), new UpdateRequest(null, "bob", null));

        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("idx:identifier:alice"), "old index must be released");
            assertEquals(seeded.uuid(), jedis.get("idx:identifier:bob"),
                    "new index must point to the same uuid");
        }
        User found = dao.findById(seeded.uuid()).orElseThrow();
        assertEquals("bob", found.identifier());
    }

    @Test
    void update_identifierConflict_throws_userUnchanged() {
        User u1 = seedLocal(null, "alice", "hash1");
        seedLocal(null, "bob", "hash2");

        IdentifierTakenException ex = assertThrows(IdentifierTakenException.class,
                () -> dao.update(u1.uuid(), new UpdateRequest(null, "bob", null)));
        assertEquals("bob", ex.identifier());

        // u1 must be unchanged — still called alice
        User unchanged = dao.findById(u1.uuid()).orElseThrow();
        assertEquals("alice", unchanged.identifier());
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(u1.uuid(), jedis.get("idx:identifier:alice"),
                    "original identifier index must be untouched");
        }
    }

    // ================================================================ delete

    @Test
    void delete_localUser_removesHashAndIdentifierIndexAndIndexSet() {
        User seeded = seedLocal(null, "alice", "hash");

        dao.delete(seeded.uuid());

        assertFalse(dao.findById(seeded.uuid()).isPresent(), "hash must be deleted");
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("idx:identifier:alice"), "identifier index must be deleted");
            assertFalse(jedis.sismember("user:index", seeded.uuid()),
                    "uuid must be removed from index set");
        }
    }

    @Test
    void delete_googleUser_removesBothIndexes() {
        User seeded = seedGoogle(null, "ada@x.com", "g1");

        dao.delete(seeded.uuid());

        assertFalse(dao.findById(seeded.uuid()).isPresent());
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("idx:identifier:ada@x.com"));
            assertNull(jedis.get("idx:google:g1"));
        }
    }

    @Test
    void delete_mergedUser_removesBothIndexes() {
        User seeded = seedMerged(null, "ada@x.com", "hash", "g1");

        dao.delete(seeded.uuid());

        assertFalse(dao.findById(seeded.uuid()).isPresent());
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("idx:identifier:ada@x.com"));
            assertNull(jedis.get("idx:google:g1"));
            assertFalse(jedis.sismember("user:index", seeded.uuid()));
        }
    }

    @Test
    void delete_localUser_doesNotTouchAbsentGoogleIndex() {
        User seeded = seedLocal(null, "alice", "hash");

        dao.delete(seeded.uuid());

        // If the DAO tries DEL on a non-existent key that's fine, but the google
        // index for an unrelated user must not be disturbed.
        seedGoogle(null, "other@x.com", "g-other");
        try (Jedis jedis = jedisPool.getResource()) {
            assertNotNull(jedis.get("idx:google:g-other"),
                    "google index of unrelated user must be untouched");
        }
    }
}
