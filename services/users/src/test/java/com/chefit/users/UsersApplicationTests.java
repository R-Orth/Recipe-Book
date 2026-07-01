package com.chefit.users;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import redis.clients.jedis.JedisPool;

@SpringBootTest
class UsersApplicationTests {

	@MockitoBean
	JedisPool jedisPool;

	@Test
	void contextLoads() {
	}

}
