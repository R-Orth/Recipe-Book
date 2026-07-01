package com.chefit.auth.dao;

import com.chefit.auth.model.User;
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

    private User newLocal(String uuid, String identifier, String hashedPassword) {
        return new User(uuid, identifier, User.looksLikeEmail(identifier),
                hashedPassword, null, List.of("local"),
                "Ada", "127.0.0.1", null, null);
    }

    private User newGoogle(String uuid, String identifier, String googleSub) {
        return new User(uuid, identifier, true,
                null, googleSub, List.of("google"),
                "Ada", "127.0.0.1", null, null);
    }

    // --- round-trip ---

    @Test
    void save_andFindByIdentifier_roundTrip_emailIdentifier() {
        User saved = dao.save(newLocal(null, "ada@x.com", "hash"));

        User found = dao.findByIdentifier("ada@x.com").orElseThrow();

        assertEquals(saved.uuid(), found.uuid());
        assertEquals("ada@x.com",  found.identifier());
        assertTrue(found.identifierIsEmail());
        assertEquals("hash",       found.hashedPassword());
        assertNull(found.googleSub());
        assertEquals(List.of("local"), found.providers());
    }

    @Test
    void save_andFindByIdentifier_roundTrip_usernameIdentifier() {
        User saved = dao.save(newLocal(null, "alice", "hash"));

        User found = dao.findByIdentifier("alice").orElseThrow();

        assertEquals(saved.uuid(), found.uuid());
        assertEquals("alice",      found.identifier());
        assertFalse(found.identifierIsEmail());
    }

    @Test
    void save_assignsUuidWhenBlank_andStampsTimestamps() {
        User saved = dao.save(newLocal(null, "alice", "hash"));

        assertNotNull(saved.uuid());
        assertFalse(saved.uuid().isBlank());
        assertNotNull(saved.createDate());
        assertNotNull(saved.modifyDate());
    }

    // --- SETNX atomic identifier claim (single-threaded) ---

    @Test
    void save_secondUserWithSameIdentifier_throwsIdentifierTaken() {
        dao.save(newLocal("u1", "alice", "hash1"));

        IdentifierTakenException ex = assertThrows(IdentifierTakenException.class,
                () -> dao.save(newLocal("u2", "alice", "hash2")));
        assertEquals("alice", ex.identifier());

        // Loser's record should not have been written.
        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("u1", jedis.get("idx:identifier:alice"));
            assertTrue(jedis.hgetAll("user:u2").isEmpty());
        }
    }

    @Test
    void save_sameUuidSameIdentifier_isIdempotent_replicationReplay() {
        dao.save(newLocal("u1", "alice", "hash"));
        // Replay (same uuid, same identifier, modified realname).
        User replayed = new User("u1", "alice", false, "hash", null,
                List.of("local"), "Ada Updated", "127.0.0.1",
                "2026-01-01T00:00:00Z", null);
        dao.save(replayed);

        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1, jedis.scard("user:index"), "replay must not duplicate user index");
            assertEquals("u1", jedis.get("idx:identifier:alice"));
        }
        User found = dao.findById("u1").orElseThrow();
        assertEquals("Ada Updated", found.realname());
        assertEquals("2026-01-01T00:00:00Z", found.createDate(), "createDate must be preserved on replay");
    }

    // --- google sub index ---

    @Test
    void save_googleAccount_claimsBothIdentifierAndGoogleIndexes() {
        dao.save(newGoogle("u1", "ada@x.com", "g1"));

        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals("u1", jedis.get("idx:identifier:ada@x.com"));
            assertEquals("u1", jedis.get("idx:google:g1"));
        }
    }

    @Test
    void findByGoogleSub_resolvesToTheRightUser() {
        dao.save(newGoogle("u1", "ada@x.com", "g1"));

        User found = dao.findByGoogleSub("g1").orElseThrow();
        assertEquals("u1", found.uuid());
        assertEquals("g1", found.googleSub());
    }

    @Test
    void findByGoogleSub_emptyWhenNoSuchSub() {
        assertTrue(dao.findByGoogleSub("ghost-sub").isEmpty());
    }

    // --- linkGoogle merge end-to-end ---

    @Test
    void linkGoogle_mergesGoogleOntoExistingLocalAccount() {
        User local = dao.save(newLocal(null, "ada@x.com", "hash"));

        User merged = dao.linkGoogle(local.uuid(), "g1");

        assertEquals("g1", merged.googleSub());
        assertTrue(merged.providers().contains("local"));
        assertTrue(merged.providers().contains("google"));
        assertEquals("hash", merged.hashedPassword(), "merge must preserve the existing password");

        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(local.uuid(), jedis.get("idx:identifier:ada@x.com"));
            assertEquals(local.uuid(), jedis.get("idx:google:g1"));
        }
    }

    @Test
    void linkGoogle_refusesLoudlyOnAccountThatAlreadyHasGoogle() {
        User google = dao.save(newGoogle("u1", "ada@x.com", "existing-sub"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> dao.linkGoogle(google.uuid(), "different-sub"));
        assertTrue(ex.getMessage().contains("already has a linked Google account"));

        // State must be unchanged.
        User unchanged = dao.findById("u1").orElseThrow();
        assertEquals("existing-sub", unchanged.googleSub());
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get("idx:google:different-sub"));
        }
    }

    // --- linkLocal end-to-end ---

    @Test
    void linkLocal_addsHashedPasswordToExistingGoogleAccount() {
        User google = dao.save(newGoogle(null, "ada@x.com", "g1"));

        User merged = dao.linkLocal(google.uuid(), "new-hash");

        assertEquals("new-hash", merged.hashedPassword());
        assertTrue(merged.providers().contains("google"));
        assertTrue(merged.providers().contains("local"));

        // Persisted.
        User found = dao.findById(google.uuid()).orElseThrow();
        assertEquals("new-hash", found.hashedPassword());
    }

    @Test
    void linkLocal_refusesLoudlyWhenAccountAlreadyHasPassword() {
        User local = dao.save(newLocal(null, "ada@x.com", "existing-hash"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> dao.linkLocal(local.uuid(), "new-hash"));
        assertTrue(ex.getMessage().contains("already has a local password set"));

        User unchanged = dao.findById(local.uuid()).orElseThrow();
        assertEquals("existing-hash", unchanged.hashedPassword());
    }

    // --- stale-index handling ---

    @Test
    void findByIdentifier_returnsEmpty_whenIndexPointsToMissingUser() {
        // Manually create an orphaned index entry, then read.
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set("idx:identifier:zombie", "u-missing");
        }

        Optional<User> result = dao.findByIdentifier("zombie");

        assertTrue(result.isEmpty());
    }

    // --- contract: DAO does not normalize ---

    @Test
    void findByIdentifier_doesNotNormalizeInput() {
        dao.save(newLocal("u1", "ada@x.com", "hash"));

        // Lookup with mixed case must MISS — normalization is the controller's job.
        assertTrue(dao.findByIdentifier("Ada@X.com").isEmpty());
        assertTrue(dao.findByIdentifier("ada@x.com").isPresent());
    }
}
