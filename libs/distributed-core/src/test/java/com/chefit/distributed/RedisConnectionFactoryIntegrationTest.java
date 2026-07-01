package com.chefit.distributed;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

// Integration tests — require a live Redis on localhost:6379 (docker compose up -d).
// These prove the data/pub-sub split actually works against a real broker.
class RedisConnectionFactoryIntegrationTest {

    RedisConnectionFactory factory;

    @BeforeEach
    void setup() {
        factory = new RedisConnectionFactory("localhost", 6379);
    }

    @AfterEach
    void tearDown() {
        factory.close();
    }

    @Test
    void dataConnection_roundTripsSetGet() {
        try (Jedis jedis = factory.dataPool().getResource()) {
            jedis.set("dc:it:key", "hello");
            assertEquals("hello", jedis.get("dc:it:key"));
            jedis.del("dc:it:key");
        }
    }

    @Test
    @Timeout(15)
    void pubSubConnection_receivesMessagePublishedOverASeparateConnection() throws Exception {
        String channel = "dc:it:channel";
        CountDownLatch subscribed = new CountDownLatch(1);
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        Jedis subscriber = factory.newPubSubConnection();
        JedisPubSub handler = new JedisPubSub() {
            @Override public void onSubscribe(String c, int count) { subscribed.countDown(); }
            @Override public void onMessage(String c, String message) { received.add(message); }
        };
        Thread subThread = new Thread(() -> subscriber.subscribe(handler, channel), "sub");
        subThread.start();

        assertTrue(subscribed.await(5, TimeUnit.SECONDS), "subscriber never became ready");

        // Publish over a pooled data connection — proves the two connection kinds are independent.
        try (Jedis publisher = factory.dataPool().getResource()) {
            publisher.publish(channel, "ping");
        }

        assertEquals("ping", received.poll(5, TimeUnit.SECONDS), "message not delivered");

        handler.unsubscribe();
        subThread.join(2000);
        subscriber.close();
    }
}
