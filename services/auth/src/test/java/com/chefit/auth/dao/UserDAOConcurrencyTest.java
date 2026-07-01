package com.chefit.auth.dao;

import com.chefit.auth.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

// Validates the atomic-claim property of UserDAO.save against real Redis. The unit test
// for save only verifies "we call setnx with these arguments"; this one verifies the
// behavior actually serializes when two writers race for the same identifier.
class UserDAOConcurrencyTest {

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

    @Test
    void twoConcurrentSavesWithSameIdentifier_exactlyOneWins() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        Runnable attempt = () -> {
            String uuid = "u-" + Thread.currentThread().threadId();
            try {
                barrier.await();
                dao.save(new User(uuid, "race", false, "hash-" + uuid, null,
                        List.of("local"), "Ada", "127.0.0.1", null, null));
                successes.incrementAndGet();
            } catch (IdentifierTakenException e) {
                conflicts.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Thread t1 = new Thread(attempt, "race-1");
        Thread t2 = new Thread(attempt, "race-2");
        t1.start(); t2.start();
        t1.join(5000); t2.join(5000);

        assertEquals(1, successes.get(), "exactly one writer should commit");
        assertEquals(1, conflicts.get(), "exactly one writer should see IdentifierTakenException");

        try (Jedis jedis = jedisPool.getResource()) {
            String winner = jedis.get("idx:identifier:race");
            assertNotNull(winner, "the identifier index must resolve to the winner");
            assertEquals(1, jedis.scard("user:index"),
                    "user index must contain exactly one entry — loser must not have been persisted");
            assertFalse(jedis.hgetAll("user:" + winner).isEmpty(),
                    "the winning uuid's user hash must exist");
        }
    }

    @Test
    void fiveConcurrentSavesWithSameIdentifier_exactlyOneWins() throws Exception {
        int contenders = 5;
        CyclicBarrier barrier = new CyclicBarrier(contenders);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger conflicts = new AtomicInteger(0);

        Thread[] threads = new Thread[contenders];
        for (int i = 0; i < contenders; i++) {
            String uuid = "u-" + i;
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    dao.save(new User(uuid, "race5", false, "hash", null,
                            List.of("local"), "Ada", "127.0.0.1", null, null));
                    successes.incrementAndGet();
                } catch (IdentifierTakenException e) {
                    conflicts.incrementAndGet();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "race5-" + i);
            threads[i].start();
        }
        for (Thread t : threads) t.join(5000);

        assertEquals(1, successes.get());
        assertEquals(contenders - 1, conflicts.get());

        try (Jedis jedis = jedisPool.getResource()) {
            assertEquals(1, jedis.scard("user:index"));
        }
    }
}
