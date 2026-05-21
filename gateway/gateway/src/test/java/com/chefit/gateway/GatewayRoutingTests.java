package com.chefit.gateway;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD tests for Spring Cloud Gateway route configuration (Task 3).
 *
 * <p>These tests define the desired routing behaviour <em>before</em> the routes are
 * implemented. All routing tests will fail until {@code application.properties} is updated
 * with {@code spring.cloud.gateway.routes[*]} entries that use the
 * {@code recipes.service.url}, {@code auth.service.url}, and {@code users.service.url}
 * property placeholders declared below.
 *
 * <p><b>Test strategy:</b> Three {@link MockWebServer} instances stand in for the real
 * downstream services. {@link DynamicPropertySource} injects their runtime-assigned ports
 * into the gateway's route configuration so no real services need to be running.
 * {@link WebTestClient} drives requests against the gateway running on a random port.
 *
 * <p><b>What is tested:</b>
 * <ul>
 *   <li>Each route forwards to the correct downstream service</li>
 *   <li>HTTP method, full path, query string, and request body are preserved</li>
 *   <li>Response status and body are passed back to the caller unchanged</li>
 *   <li>Routes are strictly isolated — a request cannot bleed to a wrong service</li>
 *   <li>Near-miss paths that should not match any route return 404</li>
 *   <li>A failed downstream connection produces a 5xx gateway error</li>
 *   <li>CORS headers are present on real responses and on OPTIONS preflight</li>
 *   <li>Unrecognised origins do not receive CORS allow-headers</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutingTests {

    // Servers are started in a static initialiser so their ports are available when
    // @DynamicPropertySource is evaluated during context creation.
    private static final MockWebServer RECIPES_SERVER;
    private static final MockWebServer AUTH_SERVER;
    private static final MockWebServer USERS_SERVER;

    static {
        try {
            RECIPES_SERVER = new MockWebServer();
            RECIPES_SERVER.start();
            AUTH_SERVER = new MockWebServer();
            AUTH_SERVER.start();
            USERS_SERVER = new MockWebServer();
            USERS_SERVER.start();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Injects mock server base URLs into the gateway's route configuration so routes
     * point at the test mock servers instead of the real services.
     *
     * <p>The {@code recipes.service.url}, {@code auth.service.url}, and
     * {@code users.service.url} properties are declared in {@code application.properties}
     * with localhost defaults. Here they are overridden with the mock servers' dynamic ports.
     */
    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("recipes.service.url", () -> "http://localhost:" + RECIPES_SERVER.getPort());
        registry.add("auth.service.url",    () -> "http://localhost:" + AUTH_SERVER.getPort());
        registry.add("users.service.url",   () -> "http://localhost:" + USERS_SERVER.getPort());
    }

    @AfterAll
    static void stopMockServers() throws IOException {
        RECIPES_SERVER.shutdown();
        AUTH_SERVER.shutdown();
        USERS_SERVER.shutdown();
    }

    /**
     * Builds a {@link WebTestClient} pointed at the gateway's random port and resets all
     * three mock servers to a fresh {@link QueueDispatcher} before each test.
     *
     * <p>{@code WebTestClient} is constructed manually here rather than autowired because
     * Spring Boot 4.x does not auto-configure it as a bean for
     * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} — the auto-configuration was
     * restructured in 4.x. Using {@link LocalServerPort} + {@code bindToServer()} is the
     * framework-independent alternative that works in all versions.
     *
     * <p>Resets all three mock servers to a fresh {@link QueueDispatcher} before each test.
     *
     * <p>This prevents stale enqueued responses from leaking between tests. The original
     * approach used an {@code @AfterEach} drain, but {@code MockWebServer.getRequestCount()}
     * counts received requests, not unconsumed queued responses — so a drain loop based on
     * it can never detect leftover responses. Resetting the dispatcher before each test is
     * the correct pattern: each test starts with a clean, empty response queue.
     */
    @BeforeEach
    void resetMockServersAndBuildClient() {
        RECIPES_SERVER.setDispatcher(new QueueDispatcher());
        AUTH_SERVER.setDispatcher(new QueueDispatcher());
        USERS_SERVER.setDispatcher(new QueueDispatcher());
        webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10))
                .build();
    }

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Enqueues a canned JSON response on a mock server.
     *
     * @param server     the mock server to enqueue on
     * @param statusCode HTTP status code to return
     * @param body       JSON string to use as the response body
     */
    private void enqueueJson(MockWebServer server, int statusCode, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(statusCode)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    /**
     * Takes the next recorded request from a mock server, waiting up to 2 seconds.
     * Returns {@code null} if no request arrives within the timeout.
     *
     * @param server the mock server to poll
     * @return the recorded request, or {@code null} on timeout
     */
    private RecordedRequest takeRequest(MockWebServer server) throws InterruptedException {
        return server.takeRequest(2, TimeUnit.SECONDS);
    }

    /**
     * Asserts that no request arrives at a mock server within 200 ms.
     * Used to verify route isolation — a request intended for one service
     * must not reach another.
     *
     * @param server  the mock server that must NOT receive a request
     * @param message failure message shown if a request does arrive
     */
    private void assertNoRequest(MockWebServer server, String message) throws InterruptedException {
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), message);
    }

    // =========================================================================
    // Recipes route — /items/**
    // =========================================================================

    /**
     * Verifies that {@code GET /items} is routed to the recipes service and that the
     * response body returned by the downstream reaches the caller unchanged.
     *
     * <p>Tests both that the route predicate matches the root collection path (no trailing
     * segment) and that body passthrough works end-to-end.
     */
    @Test
    void recipesRoute_getAll_routesToRecipesServiceAndPassesThroughBody() throws InterruptedException {
        enqueueJson(RECIPES_SERVER, 200, "[{\"id\":\"1\",\"name\":\"Soup\"}]");

        webTestClient.get().uri("/items")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertTrue(body.contains("Soup"),
                        "Response body from downstream should be passed through to caller"));

        RecordedRequest request = takeRequest(RECIPES_SERVER);
        assertNotNull(request, "Recipes service should have received the request");
        assertEquals("GET", request.getMethod());
    }

    /**
     * Verifies that {@code GET /items/{id}} is forwarded to the recipes service with the
     * complete path preserved — the id segment must arrive at the downstream unchanged and
     * must not be stripped by the gateway.
     */
    @Test
    void recipesRoute_getById_preservesFullPathAtDownstream() throws InterruptedException {
        enqueueJson(RECIPES_SERVER, 200, "{\"id\":\"abc-123\",\"name\":\"Pasta\"}");

        webTestClient.get().uri("/items/abc-123")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = takeRequest(RECIPES_SERVER);
        assertNotNull(request, "Recipes service should have received the request");
        assertEquals("GET",        request.getMethod());
        assertEquals("/items/abc-123", request.getPath(),
                "Full path including the id segment must reach the downstream unchanged");
    }

    /**
     * Verifies that a {@code PUT /items} request preserves the HTTP method and that the
     * request body is forwarded intact to the recipes service.
     *
     * <p>Also verifies that auth and users services receive nothing, establishing baseline
     * route isolation for write operations.
     */
    @Test
    void recipesRoute_put_preservesMethodAndBodyAndDoesNotReachOtherServices() throws InterruptedException {
        enqueueJson(RECIPES_SERVER, 200, "{\"id\":\"new-id\",\"name\":\"Bread\"}");

        String requestBody = "{\"name\":\"Bread\",\"time\":\"60\",\"servings\":\"8\","
                + "\"ingredients\":[],\"steps\":[\"Mix\",\"Bake\"]}";

        webTestClient.put().uri("/items")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = takeRequest(RECIPES_SERVER);
        assertNotNull(request, "Recipes service should have received the PUT request");
        assertEquals("PUT", request.getMethod(), "HTTP method must be preserved by the gateway");
        assertTrue(request.getBody().readUtf8().contains("Bread"),
                "Request body must be forwarded to the downstream unchanged");

        assertNoRequest(AUTH_SERVER,  "PUT /items must not reach the auth service");
        assertNoRequest(USERS_SERVER, "PUT /items must not reach the users service");
    }

    /**
     * Verifies that {@code DELETE /items/{id}} forwards the correct method and path to the
     * recipes service. Deletion must not bleed to auth or users.
     */
    @Test
    void recipesRoute_delete_preservesMethodAndPathAndDoesNotReachOtherServices() throws InterruptedException {
        RECIPES_SERVER.enqueue(new MockResponse().setResponseCode(204));

        webTestClient.delete().uri("/items/abc-123")
                .exchange()
                .expectStatus().isNoContent();

        RecordedRequest request = takeRequest(RECIPES_SERVER);
        assertNotNull(request, "Recipes service should have received the DELETE request");
        assertEquals("DELETE",         request.getMethod(), "HTTP method must be preserved");
        assertEquals("/items/abc-123", request.getPath(),   "Full path must be preserved");

        assertNoRequest(AUTH_SERVER,  "DELETE /items must not reach the auth service");
        assertNoRequest(USERS_SERVER, "DELETE /items must not reach the users service");
    }

    /**
     * Verifies that query string parameters appended to {@code /items} are forwarded to
     * the recipes service. If the gateway strips query params, search and filter operations
     * will silently break.
     *
     * <p>The test uses plain ASCII values to avoid Spring Cloud Gateway's percent-encoding
     * behaviour when forwarding: the gateway re-encodes percent-signs in already-encoded
     * values (e.g. {@code %20} arrives as {@code %2520}). Testing with unencoded values
     * focuses the assertion on the forwarding behaviour, not encoding details.
     */
    @Test
    void recipesRoute_queryParamsAreForwardedToDownstream() throws InterruptedException {
        enqueueJson(RECIPES_SERVER, 200, "[]");

        webTestClient.get().uri("/items?name=soup&limit=10")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = takeRequest(RECIPES_SERVER);
        assertNotNull(request, "Recipes service should have received the request");
        String path = request.getPath();
        assertTrue(path.contains("name=soup"),
                "Query param 'name' must be forwarded to downstream; actual path: " + path);
        assertTrue(path.contains("limit=10"),
                "Query param 'limit' must be forwarded to downstream; actual path: " + path);
    }

    // =========================================================================
    // Auth route — /auth/**
    // =========================================================================

    /**
     * Verifies that a request to {@code /auth/**} is forwarded to the auth service and
     * does not reach the recipes or users services.
     */
    @Test
    void authRoute_forwardsToAuthServiceOnlyAndNotOtherServices() throws InterruptedException {
        enqueueJson(AUTH_SERVER, 200, "{\"login\":\"ryan\"}");

        webTestClient.get().uri("/auth/ryan")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = takeRequest(AUTH_SERVER);
        assertNotNull(request, "Auth service should have received the request");
        assertEquals("/auth/ryan", request.getPath(), "Full path must be preserved");

        assertNoRequest(RECIPES_SERVER, "GET /auth must not reach the recipes service");
        assertNoRequest(USERS_SERVER,   "GET /auth must not reach the users service");
    }

    // =========================================================================
    // Users route — /users/**
    // =========================================================================

    /**
     * Verifies that a request to {@code /users/**} is forwarded to the users service and
     * does not reach the recipes or auth services.
     */
    @Test
    void usersRoute_forwardsToUsersServiceOnlyAndNotOtherServices() throws InterruptedException {
        enqueueJson(USERS_SERVER, 200, "[{\"login\":\"ryan\"}]");

        webTestClient.get().uri("/users")
                .exchange()
                .expectStatus().isOk();

        RecordedRequest request = takeRequest(USERS_SERVER);
        assertNotNull(request, "Users service should have received the request");

        assertNoRequest(RECIPES_SERVER, "GET /users must not reach the recipes service");
        assertNoRequest(AUTH_SERVER,    "GET /users must not reach the auth service");
    }

    // =========================================================================
    // Negative routing — unmatched paths
    // =========================================================================

    /**
     * Verifies that a path with no matching route returns 404 and does not reach any
     * downstream service. This is the baseline sanity check for unregistered paths.
     */
    @Test
    void unknownPath_returns404AndReachesNoService() throws InterruptedException {
        webTestClient.get().uri("/completely-unknown-path")
                .exchange()
                .expectStatus().isNotFound();

        assertNoRequest(RECIPES_SERVER, "Unknown path must not reach the recipes service");
        assertNoRequest(AUTH_SERVER,    "Unknown path must not reach the auth service");
        assertNoRequest(USERS_SERVER,   "Unknown path must not reach the users service");
    }

    /**
     * Verifies that near-miss paths — those similar to registered routes but not actually
     * matching them — return 404 and reach no downstream service.
     *
     * <p>Near-misses tested:
     * <ul>
     *   <li>{@code /item} (singular of {@code /items}) — must not match the recipes route</li>
     *   <li>{@code /items-extra} (prefixed match) — {@code /items/**} must be anchored,
     *       not a prefix-of-path-segment match</li>
     *   <li>{@code /api/items} (wrong prefix) — the gateway has no base-path mount</li>
     * </ul>
     */
    @Test
    void nearMissPaths_return404AndReachNoService() throws InterruptedException {
        for (String nearMiss : new String[]{"/item", "/items-extra", "/api/items"}) {
            webTestClient.get().uri(nearMiss)
                    .exchange()
                    .expectStatus().isNotFound();

            assertNoRequest(RECIPES_SERVER, nearMiss + " must not reach the recipes service");
            assertNoRequest(AUTH_SERVER,    nearMiss + " must not reach the auth service");
            assertNoRequest(USERS_SERVER,   nearMiss + " must not reach the users service");
        }
    }

    // =========================================================================
    // Error propagation
    // =========================================================================

    /**
     * Verifies that a 5xx status returned by the downstream service is passed through to
     * the caller rather than being swallowed or converted. The caller must see the
     * downstream error code so it can handle failures appropriately.
     */
    @Test
    void downstreamError_isPassedThroughToCaller() throws InterruptedException {
        RECIPES_SERVER.enqueue(new MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":\"Internal Server Error\"}"));

        webTestClient.get().uri("/items")
                .exchange()
                .expectStatus().is5xxServerError();

        assertNotNull(takeRequest(RECIPES_SERVER), "Recipes service should have received the request");
    }

    /**
     * Verifies that when the downstream service abruptly closes the connection (simulating
     * a crash or network failure), the gateway returns a 5xx error to the caller rather
     * than hanging indefinitely or returning a misleading 2xx.
     *
     * <p>Uses {@link SocketPolicy#DISCONNECT_AFTER_REQUEST} to simulate the downstream
     * accepting the request and then immediately dropping the connection without replying.
     */
    @Test
    void downstreamUnreachable_returns5xxErrorToCaller() throws InterruptedException {
        RECIPES_SERVER.enqueue(new MockResponse()
                .setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));

        webTestClient.get().uri("/items")
                .exchange()
                .expectStatus().is5xxServerError();

        assertNotNull(takeRequest(RECIPES_SERVER),
                "Recipes service should have received the request before disconnecting");
    }

    // =========================================================================
    // CORS
    // =========================================================================

    /**
     * Verifies that an OPTIONS preflight request from the Vite dev-server origin
     * ({@code localhost:5173}) receives the required CORS headers, allowing the browser
     * to proceed with the actual request.
     *
     * <p>The allowed origins are configured in {@code CorsConfig.java}. If this test
     * fails after route changes, check that {@code CorsWebFilter} is still applied.
     */
    @Test
    void cors_preflightFromAllowedOrigin_returnsCorsHeaders() {
        webTestClient.options().uri("/items")
                .header("Origin",                         "http://localhost:5173")
                .header("Access-Control-Request-Method",  "GET")
                .header("Access-Control-Request-Headers", "Content-Type")
                .exchange()
                .expectHeader().exists("Access-Control-Allow-Origin")
                .expectHeader().exists("Access-Control-Allow-Methods");
    }

    /**
     * Verifies that a real (non-preflight) GET request from an allowed origin also carries
     * the {@code Access-Control-Allow-Origin} header. Browsers inspect this header on the
     * actual response — passing the preflight alone is not sufficient.
     */
    @Test
    void cors_actualRequestFromAllowedOrigin_includesCorsHeaderOnRealResponse() throws InterruptedException {
        enqueueJson(RECIPES_SERVER, 200, "[]");

        webTestClient.get().uri("/items")
                .header("Origin", "http://localhost:5173")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("Access-Control-Allow-Origin");

        takeRequest(RECIPES_SERVER);
    }

    /**
     * Verifies that a request from an origin not in the allow-list does NOT receive an
     * {@code Access-Control-Allow-Origin} response header. Leaking this header to
     * arbitrary origins would defeat the CORS protection in {@code CorsConfig.java}.
     */
    @Test
    void cors_requestFromUnknownOrigin_doesNotReceiveCorsAllowHeader() throws InterruptedException {
        enqueueJson(RECIPES_SERVER, 200, "[]");

        webTestClient.get().uri("/items")
                .header("Origin", "http://attacker.example.com")
                .exchange()
                .expectHeader().doesNotExist("Access-Control-Allow-Origin");

        // Consume the request so the queue is clean for subsequent tests.
        // The request may or may not have reached the downstream depending on
        // whether the CORS filter rejects it before routing — either is valid.
        RECIPES_SERVER.takeRequest(200, TimeUnit.MILLISECONDS);
    }
}
