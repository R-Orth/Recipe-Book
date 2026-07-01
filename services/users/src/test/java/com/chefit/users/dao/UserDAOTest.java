package com.chefit.users.dao;

import com.chefit.users.model.User;
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
import java.util.Set;

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

    private Map<String, String> storedFields(String uuid, String identifier,
                                              boolean identifierIsEmail, String hashedPassword,
                                              String googleSub, String providersJson) {
        Map<String, String> fields = new HashMap<>();
        fields.put("uuid",              uuid);
        fields.put("identifier",        identifier == null ? "" : identifier);
        fields.put("identifierIsEmail", String.valueOf(identifierIsEmail));
        fields.put("hashedPassword",    hashedPassword == null ? "" : hashedPassword);
        fields.put("googleSub",         googleSub == null ? "" : googleSub);
        fields.put("providers",         providersJson);
        fields.put("realname",          "Ada");
        fields.put("ip",                "127.0.0.1");
        fields.put("createDate",        "2026-01-01T00:00:00Z");
        fields.put("modifyDate",        "2026-01-01T00:00:00Z");
        return fields;
    }

    // ================================================================ findAll

    @Test
    @SuppressWarnings("unchecked")
    void findAll_returnsAllUsersFromIndexSet() {
        when(jedis.smembers("user:index")).thenReturn(Set.of("u1", "u2"));
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));
        when(jedis.hgetAll("user:u2")).thenReturn(storedFields("u2", "ada@x.com",
                true, null, "g1", "[\"google\"]"));

        List<User> result = dao.findAll();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(u -> "u1".equals(u.uuid())));
        assertTrue(result.stream().anyMatch(u -> "u2".equals(u.uuid())));
    }

    @Test
    void findAll_emptyWhenIndexSetIsEmpty() {
        when(jedis.smembers("user:index")).thenReturn(Set.of());

        List<User> result = dao.findAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_skipsOrphanedIndexEntry() {
        // A UUID exists in the index set but its hash was already deleted.
        when(jedis.smembers("user:index")).thenReturn(Set.of("ghost-uuid"));
        when(jedis.hgetAll("user:ghost-uuid")).thenReturn(Map.of());

        List<User> result = dao.findAll();

        assertTrue(result.isEmpty());
    }

    // ================================================================ findById

    @Test
    void findById_returnsUserWhenHashExists() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));

        Optional<User> result = dao.findById("u1");

        assertTrue(result.isPresent());
        assertEquals("u1", result.get().uuid());
        assertEquals("alice", result.get().identifier());
        assertFalse(result.get().identifierIsEmail());
        assertEquals("hash", result.get().hashedPassword());
    }

    @Test
    void findById_emptyWhenHashMissing() {
        when(jedis.hgetAll("user:missing")).thenReturn(Map.of());

        assertTrue(dao.findById("missing").isEmpty());
    }

    // ================================================================ update — realname

    @Test
    @SuppressWarnings("unchecked")
    void update_realnameOnly_hsetsRealnameAndModifyDate() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        dao.update("u1", new UpdateRequest("New Name", null, null));

        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        Map<String, String> written = mapCaptor.getValue();
        assertEquals("New Name", written.get("realname"));
        assertNotNull(written.get("modifyDate"));
        // identifier and hashedPassword must not be stomped when not in the request
        assertNull(written.get("identifier"));
        assertNull(written.get("hashedPassword"));
    }

    @Test
    void update_modifyDateIsAlwaysRefreshedOnWrite() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        dao.update("u1", new UpdateRequest("Any Name", null, null));

        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        String writtenModifyDate = mapCaptor.getValue().get("modifyDate");
        assertNotNull(writtenModifyDate);
        assertFalse(writtenModifyDate.isBlank());
        // Must differ from the fixture's stored modifyDate
        assertNotEquals("2026-01-01T00:00:00Z", writtenModifyDate);
    }

    @Test
    void update_emptyRequest_returnsCurrentUserWithNoHset() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));

        User result = dao.update("u1", new UpdateRequest(null, null, null));

        assertNotNull(result);
        verify(jedis, never()).hset(anyString(), anyMap());
    }

    // ================================================================ update — password

    @Test
    @SuppressWarnings("unchecked")
    void update_newHashedPassword_hsetsHashedPasswordField() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "old-hash", null, "[\"local\"]"));
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        dao.update("u1", new UpdateRequest(null, null, "new-hash"));

        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        assertEquals("new-hash", mapCaptor.getValue().get("hashedPassword"));
    }

    // ================================================================ update — identifier change

    @Test
    @SuppressWarnings("unchecked")
    void update_identifierChange_claimsNewSetnxAndReleasesOld() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));
        when(jedis.setnx("idx:identifier:bob", "u1")).thenReturn(1L);
        ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

        dao.update("u1", new UpdateRequest(null, "bob", null));

        verify(jedis).setnx("idx:identifier:bob", "u1");
        verify(jedis).del("idx:identifier:alice");
        verify(jedis).hset(eq("user:u1"), mapCaptor.capture());
        assertEquals("bob", mapCaptor.getValue().get("identifier"));
    }

    @Test
    void update_identifierChange_conflictOnSetnx_throwsIdentifierTaken_noHset() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));
        when(jedis.setnx("idx:identifier:bob", "u1")).thenReturn(0L);

        assertThrows(IdentifierTakenException.class,
                () -> dao.update("u1", new UpdateRequest(null, "bob", null)));

        verify(jedis, never()).hset(anyString(), anyMap());
        // Old identifier index must not be released when the new claim failed
        verify(jedis, never()).del("idx:identifier:alice");
    }

    @Test
    void update_identifierChange_rollsBackNewClaimOnHsetFailure() {
        // SETNX on new identifier wins, but the subsequent HSET fails.
        // The new claim must be released; the old identifier index must stay intact.
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));
        when(jedis.setnx("idx:identifier:bob", "u1")).thenReturn(1L);
        when(jedis.hset(eq("user:u1"), anyMap())).thenThrow(new RuntimeException("redis down"));

        assertThrows(RuntimeException.class,
                () -> dao.update("u1", new UpdateRequest(null, "bob", null)));

        verify(jedis).del("idx:identifier:bob");       // rollback new claim
        verify(jedis, never()).del("idx:identifier:alice"); // old claim must remain
    }

    // ================================================================ delete

    @Test
    void delete_localUser_removesHashIdentifierIndexAndIndexSet() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "alice",
                false, "hash", null, "[\"local\"]"));

        dao.delete("u1");

        verify(jedis).del("user:u1");
        verify(jedis).del("idx:identifier:alice");
        verify(jedis).srem("user:index", "u1");
        verify(jedis, never()).del(startsWith("idx:google:"));
    }

    @Test
    void delete_googleUser_removesBothIndexes() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "ada@x.com",
                true, null, "g1", "[\"google\"]"));

        dao.delete("u1");

        verify(jedis).del("user:u1");
        verify(jedis).del("idx:identifier:ada@x.com");
        verify(jedis).del("idx:google:g1");
        verify(jedis).srem("user:index", "u1");
    }

    @Test
    void delete_mergedUser_removesBothIndexes() {
        when(jedis.hgetAll("user:u1")).thenReturn(storedFields("u1", "ada@x.com",
                true, "hash", "g1", "[\"local\",\"google\"]"));

        dao.delete("u1");

        verify(jedis).del("user:u1");
        verify(jedis).del("idx:identifier:ada@x.com");
        verify(jedis).del("idx:google:g1");
        verify(jedis).srem("user:index", "u1");
    }
}
