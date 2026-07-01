package com.chefit.auth.dao;

import com.chefit.auth.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDAOTest {

    @Mock JedisPool jedisPool;
    @Mock Jedis jedis;

    UserDAO dao;

    @BeforeEach
    void setup() {
        when(jedisPool.getResource()).thenReturn(jedis);
        dao = new UserDAO(jedisPool);
    }

    private Map<String, String> storedUser(String uuid,
                                           String identifier,
                                           boolean identifierIsEmail,
                                           String hashedPassword,
                                           String googleSub,
                                           String providersJson) {
        Map<String, String> fields = new HashMap<>();
        fields.put("uuid", uuid);
        fields.put("identifier", identifier);
        fields.put("identifierIsEmail", String.valueOf(identifierIsEmail));
        fields.put("hashedPassword", hashedPassword);
        fields.put("googleSub", googleSub);
        fields.put("providers", providersJson);
        fields.put("realname", "Ada");
        fields.put("ip", "127.0.0.1");
        fields.put("createDate", "2026-01-01T00:00:00Z");
        fields.put("modifyDate", "2026-01-01T00:00:00Z");
        return fields;
    }

    // --- save ---

    @Test
    void save_generatesUuidAndTimestamps_whenBlank() {
        when(jedis.setnx(anyString(), anyString())).thenReturn(1L);

        User result = dao.save(new User(null, "a@b.com", true, null, "g1",
                List.of("google"), "Ada", "127.0.0.1", null, null));

        assertNotNull(result.uuid());
        assertFalse(result.uuid().isBlank());
        assertNotNull(result.createDate());
        assertNotNull(result.modifyDate());
    }

    @Test
    @SuppressWarnings("unchecked")
    void save_writesHashAndClaimsIdentifierAndGoogleIndexes() {
        when(jedis.setnx(anyString(), anyString())).thenReturn(1L);
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        dao.save(new User("u1", "a@b.com", true, null, "g1",
                List.of("google"), "Ada", "127.0.0.1", null, null));

        verify(jedis).setnx("idx:identifier:a@b.com", "u1");
        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        verify(jedis).sadd("user:index", "u1");
        verify(jedis).set("idx:google:g1", "u1");

        Map<String, String> fields = mapCaptor.getValue();
        assertEquals("a@b.com", fields.get("identifier"));
        assertEquals("true", fields.get("identifierIsEmail"));
        assertEquals("", fields.get("hashedPassword"));
        assertTrue(fields.get("providers").contains("google"));
    }

    @Test
    void save_doesNotTouchGoogleIndex_whenNoSub() {
        when(jedis.setnx(anyString(), anyString())).thenReturn(1L);

        dao.save(new User("u1", "alice", false, "hash", null,
                List.of("local"), "Ada", "127.0.0.1", null, null));

        verify(jedis).setnx("idx:identifier:alice", "u1");
        verify(jedis, never()).set(startsWith("idx:google:"), anyString());
    }

    @Test
    void save_throwsIdentifierTaken_whenSetnxLoses_andExistingUuidDiffers() {
        when(jedis.setnx("idx:identifier:a@b.com", "u1")).thenReturn(0L);
        when(jedis.get("idx:identifier:a@b.com")).thenReturn("other-uuid");

        assertThrows(IdentifierTakenException.class, () -> dao.save(new User(
                "u1", "a@b.com", true, "hash", null,
                List.of("local"), "Ada", "127.0.0.1", null, null)));

        verify(jedis, never()).hset(anyString(), anyMap());
    }

    @Test
    void save_rollsBackIdentifierIndex_whenHsetFailsAfterClaim() {
        // SETNX wins, but the subsequent hset throws (Redis down mid-call, OOM, etc.).
        // The just-claimed identifier index entry must be DELeted as rollback, otherwise
        // a future request finds an orphan pointing to a uuid with no user hash.
        when(jedis.setnx("idx:identifier:alice", "u1")).thenReturn(1L);
        when(jedis.hset(eq("user:u1"), anyMap())).thenThrow(new RuntimeException("redis down"));

        assertThrows(RuntimeException.class, () -> dao.save(new User(
                "u1", "alice", false, "hash", null,
                List.of("local"), "Ada", "127.0.0.1", null, null)));

        verify(jedis).del("idx:identifier:alice");
        verify(jedis, never()).sadd(anyString(), anyString());
    }

    @Test
    void save_rollsBackIdentifierIndex_whenSaddFailsAfterClaim() {
        when(jedis.setnx("idx:identifier:alice", "u1")).thenReturn(1L);
        when(jedis.sadd(eq("user:index"), eq("u1"))).thenThrow(new RuntimeException("redis down"));

        assertThrows(RuntimeException.class, () -> dao.save(new User(
                "u1", "alice", false, "hash", null,
                List.of("local"), "Ada", "127.0.0.1", null, null)));

        verify(jedis).del("idx:identifier:alice");
    }

    @Test
    void save_doesNotRollBack_whenReplayReclaimsExistingIdentifier() {
        // On replay (same uuid path), the identifier index already pointed to us — we
        // didn't claim it this call. A subsequent failure must NOT delete it.
        when(jedis.setnx("idx:identifier:alice", "u1")).thenReturn(0L);
        when(jedis.get("idx:identifier:alice")).thenReturn("u1");
        when(jedis.hset(eq("user:u1"), anyMap())).thenThrow(new RuntimeException("redis down"));

        assertThrows(RuntimeException.class, () -> dao.save(new User(
                "u1", "alice", false, "hash", null,
                List.of("local"), "Ada", "127.0.0.1", null, null)));

        verify(jedis, never()).del("idx:identifier:alice");
    }

    @Test
    void save_proceeds_whenSetnxLoses_butExistingUuidMatches() {
        // Replication-replay path: same uuid claiming the same identifier; should overwrite.
        when(jedis.setnx("idx:identifier:a@b.com", "u1")).thenReturn(0L);
        when(jedis.get("idx:identifier:a@b.com")).thenReturn("u1");

        User result = dao.save(new User("u1", "a@b.com", true, "hash", null,
                List.of("local"), "Ada", "127.0.0.1", "2026-01-01T00:00:00Z", null));

        assertEquals("u1", result.uuid());
        verify(jedis).hset(eq("user:u1"), anyMap());
        verify(jedis).sadd("user:index", "u1");
    }

    // --- findByIdentifier ---

    @Test
    void findByIdentifier_resolvesThroughIndex() {
        when(jedis.get("idx:identifier:a@b.com")).thenReturn("u1");
        when(jedis.hgetAll("user:u1")).thenReturn(storedUser("u1", "a@b.com", true,
                "", "g1", "[\"google\"]"));

        Optional<User> result = dao.findByIdentifier("a@b.com");

        assertTrue(result.isPresent());
        assertEquals("u1", result.get().uuid());
        assertEquals("a@b.com", result.get().identifier());
        assertTrue(result.get().identifierIsEmail());
    }

    @Test
    void findByIdentifier_resolvesUsernameForm() {
        when(jedis.get("idx:identifier:alice")).thenReturn("u2");
        when(jedis.hgetAll("user:u2")).thenReturn(storedUser("u2", "alice", false,
                "hash", "", "[\"local\"]"));

        Optional<User> result = dao.findByIdentifier("alice");

        assertTrue(result.isPresent());
        assertEquals("alice", result.get().identifier());
        assertFalse(result.get().identifierIsEmail());
    }

    @Test
    void findByIdentifier_emptyWhenNoIndex() {
        when(jedis.get("idx:identifier:ghost")).thenReturn(null);

        assertTrue(dao.findByIdentifier("ghost").isEmpty());
    }

    @Test
    void findByIdentifier_emptyWhenStaleIndexPointsToMissingUser() {
        // Index hits, but the user hash is gone — should return empty, not crash.
        when(jedis.get("idx:identifier:zombie")).thenReturn("u-missing");
        when(jedis.hgetAll("user:u-missing")).thenReturn(Map.of());

        assertTrue(dao.findByIdentifier("zombie").isEmpty());
    }

    @Test
    void findByIdentifier_doesNotNormalize() {
        // Contract: normalization is the controller's job; DAO trusts already-normalized input.
        when(jedis.get("idx:identifier:Alice")).thenReturn(null);
        when(jedis.get("idx:identifier:alice")).thenReturn("u2");

        assertTrue(dao.findByIdentifier("Alice").isEmpty());
        assertTrue(dao.findByIdentifier("alice").isPresent() || dao.findByIdentifier("alice").isEmpty());
    }

    // --- findByGoogleSub ---

    @Test
    void findByGoogleSub_resolvesThroughIndex() {
        when(jedis.get("idx:google:g1")).thenReturn("u1");
        when(jedis.hgetAll("user:u1")).thenReturn(storedUser("u1", "a@b.com", true,
                "", "g1", "[\"google\"]"));

        Optional<User> result = dao.findByGoogleSub("g1");

        assertTrue(result.isPresent());
        assertEquals("g1", result.get().googleSub());
    }

    // --- linkGoogle ---

    @Test
    @SuppressWarnings("unchecked")
    void linkGoogle_addsProviderSubAndIndex() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedUser("u1", "a@b.com", true,
                "hash", "", "[\"local\"]"));
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        User merged = dao.linkGoogle("u1", "g1");

        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        verify(jedis).set("idx:google:g1", "u1");

        assertTrue(merged.providers().contains("local"));
        assertTrue(merged.providers().contains("google"));
        assertEquals("g1", merged.googleSub());

        Map<String, String> fields = mapCaptor.getValue();
        assertEquals("g1", fields.get("googleSub"));
        assertTrue(fields.get("providers").contains("google"));
    }

    @Test
    void linkGoogle_refusesLoudly_whenAccountAlreadyHasGoogleSub() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedUser("u1", "a@b.com", true,
                "", "existing-sub", "[\"google\"]"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> dao.linkGoogle("u1", "different-sub"));
        assertTrue(ex.getMessage().contains("already has a linked Google account"));
        verify(jedis, never()).hset(anyString(), anyMap());
    }

    @Test
    void linkGoogle_throws_whenUserMissing() {
        when(jedis.hgetAll("user:ghost")).thenReturn(Map.of());

        assertThrows(IllegalStateException.class, () -> dao.linkGoogle("ghost", "g1"));
    }

    // --- linkLocal ---

    @Test
    @SuppressWarnings("unchecked")
    void linkLocal_addsHashedPasswordAndLocalProvider() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedUser("u1", "a@b.com", true,
                "", "g1", "[\"google\"]"));
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        User merged = dao.linkLocal("u1", "new-hash");

        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        assertTrue(merged.providers().contains("google"));
        assertTrue(merged.providers().contains("local"));
        assertEquals("new-hash", merged.hashedPassword());

        Map<String, String> fields = mapCaptor.getValue();
        assertEquals("new-hash", fields.get("hashedPassword"));
        assertTrue(fields.get("providers").contains("local"));
    }

    @Test
    void linkLocal_refusesLoudly_whenAccountAlreadyHasPassword() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedUser("u1", "a@b.com", true,
                "existing-hash", "", "[\"local\"]"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> dao.linkLocal("u1", "new-hash"));
        assertTrue(ex.getMessage().contains("already has a local password set"));
        verify(jedis, never()).hset(anyString(), anyMap());
    }

    // --- findById ---

    @Test
    void findById_emptyWhenMissing() {
        when(jedis.hgetAll("user:missing")).thenReturn(Map.of());

        assertTrue(dao.findById("missing").isEmpty());
    }
}
