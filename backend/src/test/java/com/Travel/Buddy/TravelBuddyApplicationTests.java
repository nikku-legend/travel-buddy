package com.Travel.Buddy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Context load smoke test.
 *
 * <p>Runs on the H2 test profile so it verifies the whole application
 * graph (security, JPA, controllers) without requiring a live MySQL
 * server. Without {@code @ActiveProfiles("test")} this test attempted to
 * connect to the real datasource configured in
 * {@code application.properties} and failed on any machine that did not
 * happen to have MySQL running.
 */
@SpringBootTest
@ActiveProfiles("test")
class TravelBuddyApplicationTests {

	@Test
	void contextLoads() {
	}

}
