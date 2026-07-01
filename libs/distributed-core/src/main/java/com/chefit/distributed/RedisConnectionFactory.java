package com.chefit.distributed;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import redis.clients.jedis.JedisPoolConfig;

// Hands out two kinds of Jedis connections from one host:port config:
//   - dataPool(): a shared, pooled connection source for ordinary commands
//   - newPubSubConnection(): a dedicated, caller-owned connection for subscribers
// The split is load-bearing: a SUBSCRIBE call blocks its thread for the life of the
// subscription and can never be returned to a pool. Consolidates the jedisPool() +
// newPubSubConnection() logic currently duplicated in each service's RedisConfig.
public class RedisConnectionFactory implements AutoCloseable {

    private final String host;
    private final int port;
    private final JedisPool dataPool;

    public RedisConnectionFactory(String host, int port) {
        this.host = host;
        this.port = port;
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(16);
        config.setMaxIdle(8);
        config.setTestOnBorrow(true);
        this.dataPool = new JedisPool(config, host, port);
    }

    // Shared pool for ordinary data commands. Same instance for the life of the factory.
    public JedisPool dataPool() {
        return dataPool;
    }

    // A fresh, dedicated connection the caller owns and must close. Never pooled — a
    // subscriber blocks its thread and can't be returned to the pool.
    public Jedis newPubSubConnection() {
        return new Jedis(host, port);
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    // Closes the shared data pool. Caller-owned pub/sub connections are not tracked here
    // and must be closed by whoever created them.
    @Override
    public void close() {
        dataPool.close();
    }
}
