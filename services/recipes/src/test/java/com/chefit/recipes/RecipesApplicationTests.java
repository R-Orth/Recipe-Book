package com.chefit.recipes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import redis.clients.jedis.JedisPool;

@SpringBootTest
class RecipesApplicationTests {

	@MockitoBean
	JedisPool jedisPool;

	@Test
	void contextLoads() {
	}
}
