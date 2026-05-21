package com.chefit.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test that verifies the Spring application context loads without errors.
 *
 * <p>This test must pass both before and after Task 3. Specifically, it confirms that
 * adding route configuration to {@code application.properties} and removing the stub
 * {@code RecipesController} does not break context initialisation.
 */
@SpringBootTest
class GatewayApplicationTests {

    /**
     * Verifies that the full Spring application context starts successfully.
     * A failure here typically indicates a misconfigured bean, a missing property,
     * or a broken auto-configuration.
     */
    @Test
    void contextLoads() {
    }
}
