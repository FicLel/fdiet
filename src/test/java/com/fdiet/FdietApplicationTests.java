package com.fdiet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Needs a reachable MySQL; skipped until the connection variables are set. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "MYSQL_USER", matches = ".+")
class FdietApplicationTests {

	@Test
	void contextLoads() {
	}

}
