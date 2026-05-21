# ChefIt Gateway

Spring Cloud Gateway (WebFlux). Receives all requests from the frontend and routes them to the appropriate microservice. The frontend should never call a service directly.

## Running

```bash
cd gateway/gateway
./gradlew bootRun    # starts on :8080
./gradlew build -x test   # build JAR without running tests
```

## Routes

Routes are defined in `gateway/gateway/src/main/resources/application.properties` using the `spring.cloud.gateway.server.webflux.routes[*]` namespace (Spring Cloud 2025.x — note the different prefix from older Spring Cloud versions).

| Path | Forwards to | Property key |
|---|---|---|
| `/items`, `/items/**` | Recipes service `:8081` | `recipes.service.url` |
| `/auth`, `/auth/**` | Auth service `:8082` | `auth.service.url` |
| `/users`, `/users/**` | Users service `:8083` | `users.service.url` |

Service URLs default to `localhost` and can be overridden per-environment:

```properties
recipes.service.url=http://my-recipes-host:8081
```

## CORS

CORS is configured via `spring.cloud.gateway.server.webflux.globalcors.*` in `application.properties`. Allowed origins: `localhost:5173` and `localhost:3000` (Vite dev server and standard dev port). All methods and headers are allowed.

To add origins for a deployed environment, append to the `allowed-origins` list in `application.properties`.

## Tests

```bash
cd gateway/gateway
./gradlew test        # runs all 15 tests
./gradlew cleanTest test   # force re-run (Gradle caches passing tests)
```

### Test classes

| Class | What it tests |
|---|---|
| `GatewayApplicationTests` | Spring context loads without errors |
| `GatewayRoutingTests` | All 14 routing behaviours |

### How the routing tests work

`GatewayRoutingTests` starts the full gateway on a random port with three `MockWebServer` instances standing in for the real downstream services. `@DynamicPropertySource` injects the mock server URLs into the route configuration so no real services need to be running — not even Redis.

The 14 routing tests cover:

| Test | What it verifies |
|---|---|
| `recipesRoute_getAll_*` | `GET /items` routes to recipes; response body passed through |
| `recipesRoute_getById_*` | Full path preserved at downstream (`/items/abc-123` not stripped to `/abc-123`) |
| `recipesRoute_put_*` | HTTP method and request body preserved; auth/users not reached |
| `recipesRoute_delete_*` | Method and path preserved; auth/users not reached |
| `recipesRoute_queryParams_*` | Query string forwarded unchanged |
| `authRoute_*` | `/auth/**` routes to auth only |
| `usersRoute_*` | `/users/**` routes to users only |
| `unknownPath_*` | Unregistered path returns 404; no service reached |
| `nearMissPaths_*` | `/item`, `/items-extra`, `/api/items` all return 404 |
| `downstreamError_*` | Downstream 5xx is passed through, not swallowed |
| `downstreamUnreachable_*` | Dropped connection produces gateway 5xx |
| `cors_preflight_*` | OPTIONS from allowed origin returns CORS headers |
| `cors_actualRequest_*` | Real requests from allowed origin include CORS headers |
| `cors_unknownOrigin_*` | Unrecognised origins do not receive CORS allow-header |

### Spring Boot / Spring Cloud 2025.x notes

- Route property namespace is `spring.cloud.gateway.server.webflux.routes[*]` — the older `spring.cloud.gateway.routes[*]` is silently ignored
- `WebTestClient` is not auto-configured as a bean in `@SpringBootTest(RANDOM_PORT)` — tests construct it manually via `@LocalServerPort` + `WebTestClient.bindToServer()`
- CORS via a `CorsWebFilter` bean conflicts with the gateway's internal filter ordering in 2025.x — use `spring.cloud.gateway.server.webflux.globalcors.*` properties instead

## Ports

| Service | Port |
|---|---|
| Gateway | 8080 |
| Recipes | 8081 |
| Auth | 8082 |
| Users | 8083 |
| Redis | 6379 |
