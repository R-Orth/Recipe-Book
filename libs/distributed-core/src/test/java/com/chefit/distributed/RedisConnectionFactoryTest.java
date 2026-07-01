package com.chefit.distributed;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests — no live Redis. Jedis/JedisPool construct lazily (no connection until a
// command), so instance-identity and config can be asserted offline.
class RedisConnectionFactoryTest {

    @Test
    void storesHostAndPort() {
        RedisConnectionFactory factory = new RedisConnectionFactory("redis.example", 6380);
        assertEquals("redis.example", factory.host());
        assertEquals(6380, factory.port());
        factory.close();
    }

    @Test
    void newPubSubConnection_returnsADistinctInstanceEachCall() {
        RedisConnectionFactory factory = new RedisConnectionFactory("localhost", 6379);
        Jedis a = factory.newPubSubConnection();
        Jedis b = factory.newPubSubConnection();
        assertNotNull(a);
        assertNotNull(b);
        assertNotSame(a, b, "each subscriber must own a fresh, dedicated connection");
        a.close();
        b.close();
        factory.close();
    }

    @Test
    void dataPool_isReusedAcrossCalls() {
        RedisConnectionFactory factory = new RedisConnectionFactory("localhost", 6379);
        assertSame(factory.dataPool(), factory.dataPool(), "the data pool is shared, not rebuilt");
        factory.close();
    }
}
