package com.chefit.recipes.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Configuration
public class RedisConfig {

    @Value("${redis.host:localhost}")
    private String host;

    @Value("${redis.port:6379}")
    private int port;

    @Bean
    public JedisPool jedisPool() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        config.setMaxIdle(8);
        config.setTestOnBorrow(true);
        return new JedisPool(config, host, port);
    }

    // Returns a dedicated blocking connection for pub/sub subscribers.
    // A subscriber blocks its thread for the lifetime of the subscription
    // and cannot be returned to the pool — callers own this connection.
    // Used by DiscoveryManager, HeartbeatManager, and ReplicationManager (Tasks 8–10).
    public Jedis newPubSubConnection() {
        return new Jedis(host, port);
    }
}
