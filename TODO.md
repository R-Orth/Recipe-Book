# ChefIt — Development Todo

## Goal
Get all services and gateway running as a distributed system backed by Redis/Jedis, with the frontend talking exclusively to the gateway.

Consistency model: **eventual consistency**. The coordinator commits a write locally and returns immediately to the client. Replication to peer instances happens asynchronously over Redis pub/sub — replicas will converge but may briefly serve stale reads. This replaces the prototype's blocking primary-backup protocol (sequential consistency), which held the response until all replicas ACKed.

Inter-node messaging uses **Redis pub/sub** instead of raw UDP multicast sockets. The legacy `MulticastSocket` setup required hardcoded network interface names (`"eno1"`) and Java object serialization. Redis pub/sub uses the same infrastructure already required for data storage and carries plain JSON strings, making the coordination layer portable and debuggable.

---

## Tasks

### ~~Task 1 — Add Jedis to `services/recipes` and implement `RecipeDAO`~~ ✓
Add `implementation 'redis.clients:jedis:5.x'` to `services/recipes/build.gradle`. Create `RecipeDAO.java` mirroring `DataAccessObject` — each recipe stored as a Redis hash keyed by its id: `HSET <id> name "..." time "..." servings "..." ingredients "..." steps "..."`. Ingredients and steps are JSON-serialized strings since Redis hashes are flat. Expose: `save(Recipe)`, `findById(String)`, `findAll()`, `delete(String)`.

**Completed.** Files added: `model/Recipe.java`, `model/Ingredient.java`, `config/RedisConfig.java`, `dao/RecipeDAO.java`. Also added `com.fasterxml.jackson.core:jackson-databind` to `build.gradle` (not transitively included by `spring-boot-starter-webmvc` in Spring Boot 4.x). `RedisConfig` exposes both a `JedisPool` bean for data operations and a `newPubSubConnection()` factory method for the dedicated blocking connections Tasks 8–10 will need.

### ~~Task 2 — Implement full CRUD in `RecipesController` (clean up `RecipesApplication`)~~ ✓
Remove the stray `@GetMapping("/hello")` from `RecipesApplication.java` — it is on the wrong class. Create a proper `RecipesController.java` with `@RestController @RequestMapping("/items")` that delegates to `RecipeDAO`:
- `GET /items` → `findAll()`, returns `List<Recipe>`
- `GET /items/{id}` → `findById(id)`, returns `Recipe` or 404
- `PUT /items` → `save(recipe)`, returns saved recipe
- `DELETE /items/{id}` → `delete(id)`, returns 204

**Completed.** `RecipesApplication` cleaned up. `controller/RecipesController.java` created. Two build fixes noted: `ObjectMapper` is not auto-configured as a Spring bean by `spring-boot-starter-webmvc` in Spring Boot 4.x — instantiated directly in `RecipeDAO` instead of injected. Test updated with `@MockitoBean JedisPool` so the context load test doesn't require a live Redis.

### ~~Task 3 — Configure Spring Cloud Gateway routes~~ ✓
Remove the stub `RecipesController` from the gateway — it conflicts with routing on the same paths. Add to `gateway/application.properties`:
```properties
spring.cloud.gateway.routes[0].id=recipes
spring.cloud.gateway.routes[0].uri=http://localhost:8081
spring.cloud.gateway.routes[0].predicates[0]=Path=/items/**
```
Add placeholder routes for auth (`/auth/**` → 8082) and users (`/users/**` → 8083) so the gateway owns all routing from day one.

**Completed.** Deleted stub `RecipesController`, `dto/Recipe`, `dto/Ingredient`, and `config/CorsConfig` from the gateway. Added three routes with comma-OR predicates (`Path=/items,/items/**` etc.) covering both bare paths and sub-paths. Three Spring Cloud 2025.x gotchas encountered and resolved:
1. Property namespace changed from `spring.cloud.gateway.routes[*]` to `spring.cloud.gateway.server.webflux.routes[*]`
2. `WebTestClient` is not auto-configured in Spring Boot 4.x `@SpringBootTest` — constructed manually via `@LocalServerPort` + `WebTestClient.bindToServer()`
3. CORS via `CorsWebFilter` bean conflicts with Gateway filter chain in 2025.x — replaced with native `spring.cloud.gateway.server.webflux.globalcors.*` properties. All 15 gateway tests pass (14 routing + 1 context load).

### ~~Task 4 — Switch the frontend from AWS to the gateway~~ ✓
In `src/utils/api.js`: make `GATEWAY_URL` the primary URL in all four functions (`fetchAllRecipes`, `fetchRecipeById`, `createRecipe`, `deleteRecipeById`), demote `AWS_URL` to a commented-out fallback. Remove the `pingGateway` fire-and-forget calls — the gateway *is* the call now.

**Completed.** All four functions now fetch `GATEWAY_URL`; the AWS endpoint (`API_URL` in the actual file, not `AWS_URL`) is demoted to a commented-out fallback and `pingGateway` is removed. No caller changes needed — the recipes service's `Recipe` JSON shape matches what the pages already consume. Also migrated the dead root QUnit suite (which imported deleted `js/*.js`) to Vitest in `frontend/ChefIt`: `quips.test.js` + fetch-mocked `api.test.js` (10 tests). Removed `test/test.js` and dropped qunit/sinon from the root.

### ~~Task 5 — Add Jedis to `services/auth` and implement `UserDAO` + `AuthController`~~ ✓
Same Jedis pattern as Task 1. `UserDAO` stores: `login`, `uuid`, `hashedPassword`, `realname`, `ip`, `createDate`, `modifyDate` as Redis hash fields. `AuthController` at `/auth`:
- `POST /auth/register` — hashes password (SHA-256), stores via DAO
- `POST /auth/login` — verifies hashed password
- `GET /auth/{login}` — returns user fields minus password

**Completed.** Schema diverged from the TODO's original spec after design iteration; here is the final state.

**Identifier model.** `User` carries a single `identifier` field (nullable username OR email) plus an `identifierIsEmail` shape hint. Both `hashedPassword` and `googleSub` are mutually optional — every account has at least one auth method. Replaces the original `login` field for clarity.

**Indexes.** One `idx:identifier:{normalized}` index handles all login-resolution; `idx:google:{sub}` is a separate backend-only resolver. Cross-uniqueness is automatic — both username and email forms claim the same keyspace.

**Atomic identifier claim.** `UserDAO.save` uses `SETNX` to claim the identifier index, and now rolls back the claim on any post-claim write failure (`hset`/`sadd`/`set`) so a crash mid-save can't leave an orphan index entry. Concurrent-race integration test (`UserDAOConcurrencyTest`) proves serialization against real Redis.

**Endpoint contract.**
- `POST /auth/google` — verify Google ID token, find-or-create/merge (only auto-merges when `email_verified`), issue ChefIt JWT.
- `POST /auth/register` — `200` with `AuthResponse` on success; `400 invalid_request` with `details[]` on validation failure; `401 registration_unavailable` on identifier conflict (deliberately uses 401 not 409 — see "no auth-method leak" below).
- `POST /auth/login` — `200` with `AuthResponse` on success; `400 invalid_request` on missing fields; `401 invalid_credentials` on every failure mode (unknown identifier, wrong password, Google-only account), with dummy `passwordHasher.verify(...)` against `FAKE_HASH` on the no-user and no-password paths to flatten response timing.
- `GET /auth/{identifier}` — self-only via JWT (`sub` claim must match the resolved user's uuid). Returns `PublicUser` with `hashedPassword`, `ip`, `googleSub`, and `modifyDate` redacted.

**Security properties.** All three login 401 paths return byte-identical bodies (no auth-method discriminator). The register conflict 401 is generic — no `google_account_exists` discriminator anywhere. The frontend handles the "did you mean Google?" UX with universal hint text on any 401, not server signaling.

**Crypto.** `PasswordHasher` is salt + pepper SHA-256, `salt$hex` storage format, constant-time `MessageDigest.isEqual` comparison, FAKE_HASH constant for the dummy-verify path, hard cap at 1024-char plaintext (DoS guard). `PasswordPolicy` requires 10-1024 chars + lowercase + uppercase + digit + special (locked by the TODO; modern guidance prefers length-only). Pepper rotation invalidates every stored hash by design.

**JWT.** Stateless HS256, `sub`/`identifier`/`name` claims, 24h expiry. Each downstream service validates the signature locally with the shared secret — no per-request Redis lookup for session validation. Frontend stores in `localStorage` and includes `Authorization: Bearer ...` on protected requests.

**Frontend.** New `LocalLoginForm` and `RegisterForm` components, both posting through the gateway. New `loginWithCredentials`/`register`/`fetchSelf` functions in `auth.js` (+ token storage + `chefit-auth` event dispatch). `LoginPage` combines Google + local + register in a tabbed UX. `SettingsPage` now calls `fetchSelf` for the real `providers` list instead of a mocked Google entry. JWT claim renamed `email`→`identifier` on both sides.

**Test count.** 116 JVM tests across the auth service: `AuthControllerTest` (38), `UserDAOTest` (20), `UserDAOIntegrationTest` (14, Redis live), `UserDAOConcurrencyTest` (2, Redis live), `PasswordHasherTest` (22), `PasswordPolicyTest` (16), `JwtServiceTest` (3), `AuthApplicationTests` (1). Frontend Vitest count is 32 across `api.test.js`, `quips.test.js`, `auth.test.js`, `LocalLoginForm.test.jsx`, `RegisterForm.test.jsx`. Live smoke through the gateway confirmed all the locked status codes and body shapes.

**Deferred but flagged.** No password recovery (a username-only user is stuck if they forget; an email user has no automated reset flow yet). No email-verification flow for local registrations. No revocation list for JWT (logout is client-side only — stateless trade-off). No startup warning when running with the dev-default `JWT_SECRET`/`PASSWORD_PEPPER`. No log-hygiene test asserting passwords never log. The `GET /auth/{identifier}` 403-vs-404 distinction does leak account existence to authenticated callers — accepted because self-only access kills the bulk-enumeration vector. Each service still validates JWTs independently with a shared secret; choosing a shared home for `JwtService` (and the other duplicated security classes) is deferred to **Task 11** — Task 7 is coordination-primitives only.

### ~~Task 6 — Add Jedis to `services/users` and implement `UsersController`~~ ✓
`UsersController` at `/users` wraps the same Redis user hashes as auth but exposes the management interface: `GET /users` (all users), `GET /users/{id}` (by UUID), `PUT /users/{id}` (modify), `DELETE /users/{id}` (delete with password verify). At this point all three services back the gateway and the frontend works end-to-end without touching AWS.

**Completed.** Built TDD-first: all tests existed against `UnsupportedOperationException` stubs; this task filled them in.

**Shared Redis layout.** `UserDAO` reads/writes the exact keyspace the auth service owns — `user:{uuid}` hash, `user:index` set, `idx:identifier:{id}` and `idx:google:{sub}` indexes. New read methods (`findAll`, `findById`) plus management mutations (`update`, `delete`); this service never creates accounts. `findAll` skips orphaned index entries (uuid in the set but hash already gone).

**Partial update with index migration.** `update(uuid, UpdateRequest)` writes only non-null fields and always refreshes `modifyDate`; empty request is a no-op (no `HSET`). Identifier changes `SETNX`-claim the new index (throw `IdentifierTakenException` on conflict, *before* any write), `HSET`, then release the old index — and roll back the new claim if the `HSET` throws, leaving the old index intact.

**Auth model per endpoint.** `GET /users` and `GET /users/{id}` accept any valid JWT (reads are open to authenticated callers — *not* self-only). `PUT`/`DELETE` are self-only: JWT `sub` must equal the path uuid (403 otherwise), checked before any DAO call. Malformed `Authorization` headers (no `Bearer `, empty token, double-space) and subject-less/invalid tokens all 401. Password change verifies current password (401 `invalid_credentials`), runs `PasswordPolicy` on the new one (400 `invalid_request` + `details[]`), and rejects Google-only accounts (400 `no_password_auth`). Delete requires password verify; both wrong-password paths return byte-identical bodies. Every response goes through `PublicUser` (redacts `hashedPassword`/`ip`/`googleSub`/`modifyDate`).

**Service classes ported from auth** — `JwtService.parse` (local HS256 validation, shared secret, no auth round-trip), `PasswordHasher` (salt+pepper SHA-256, shared pepper so hashes cross-verify), `PasswordPolicy` (locked 10–1024 + 4 char-classes). Deliberate interim duplication; choosing a shared home and consolidating these is deferred to **Task 11** (Task 7 covers coordination primitives only).

**Test count.** 70 JVM tests, all green: `UsersControllerTest` (41), `UserDAOTest` (15), `UserDAOIntegrationTest` (13, Redis live), `UsersApplicationTests` (1). One mechanical test-harness fix: the `mockValidToken` helper nested `claimsWith(...)` inside `when(...).thenReturn(...)`, a Mockito misuse (`UnfinishedStubbingException`) that broke 26 tests at setup regardless of production code — de-nested to match the working auth pattern; the asserted contract is unchanged.

**Live smoke verified.** Drove the full flow through the gateway (`:8080` → users `:8083`), minting a real JWT via `POST /auth/register`: list/read redact sensitive fields, no-token → 401, self `PUT` → 200, other-user `PUT` → 403, wrong-password `DELETE` → 401, correct-password `DELETE` → 204, then read of the deleted user → 404. All as expected.

### ~~Task 7 — Extract a shared `distributed-core` Gradle subproject~~ ✓
Tasks 8–10 repeat across all three services. Create `libs/distributed-core/` as a Gradle subproject containing:
- `PubSubChannels.java` — channel name constants (`chefit:{service}:discovery`, `:election`, `:heartbeat`, `:replication`)
- `RedisConnectionFactory.java` — creates separate Jedis connections for data and pub/sub (a subscriber connection blocks its thread and cannot be reused for commands)
- `NodeInfo.java` — holds this instance's id (`ProcessHandle.current().pid()`), IP, and service name

Do **not** port `ServerMessage.java`, `Request.java`, `Listener.java`, or `Utility.java` — those existed to serialize Java objects over UDP sockets. All inter-node messages are now plain JSON strings over Redis pub/sub; Spring's `ObjectMapper` handles serialization.

Include in recipes/auth/users `build.gradle` via `implementation project(':libs:distributed-core')`.

**Scope: coordination primitives ONLY.** De-duplicating the shared security classes (`JwtService`/`PasswordHasher`/`PasswordPolicy`) is **out of scope** here — it is tracked separately as **Task 11** (decision pending). Keep this task to the three classes above so the risky build restructure stays isolated and Tasks 8–10 unblock fastest.

**Structural prerequisite (the real work).** `implementation project(':libs:distributed-core')` does **not** work with the current layout: each service (`services/recipes`, `services/auth`, `services/users`) is its *own independent Gradle build* with its own `settings.gradle`. A `project(':...')` dependency requires them to be subprojects of **one** build. So Task 7 must first either:
1. restructure into a single root multi-project build — a root `settings.gradle` with `include ':services:recipes', ':services:auth', ':services:users', ':libs:distributed-core'`, or
2. use a Gradle **composite build** (`includeBuild`) that keeps the services independent.

This Gradle wiring is most of the effort/risk and isn't unit-testable — it is verified by regression: every existing suite (recipes; auth 116; users 70) must stay green after the restructure.

**Completed.** Built TDD-first (red → green).

**Module.** `libs/distributed-core/` is a standalone `java-library` build (own wrapper + `settings.gradle`; pinned dep versions — jedis 5.2.0, jackson 2.18.2, junit 5.11.4 — since there's no Spring BOM here). Package `com.chefit.distributed`. Three classes, 26 tests green:
- `PubSubChannels` — static `discovery/election/heartbeat/replication(service)` → `chefit:{service}:{purpose}`; null/blank service throws `IllegalArgumentException`. (13 tests)
- `NodeInfo` — `record(id, ip, service)` + `create(service)`: id = `String.valueOf(ProcessHandle.current().pid())` (String so it drops into the DiscoveryManager `id→ip` map and serializes as a JSON string), ip = `getLocalHost().getHostAddress()` with loopback fallback on `UnknownHostException`. (8 tests)
- `RedisConnectionFactory` — one shared `JedisPool` for data + fresh caller-owned `Jedis` per `newPubSubConnection()`; `close()` releases only the pool. (3 unit + 2 live-Redis integration: SET/GET, and subscribe-on-dedicated receives publish-on-pooled — the data/pub-sub split proof.)

**Build wiring — composite build, not multi-project.** Chose Gradle **composite build** (`includeBuild '../../libs/distributed-core'` in each service's `settings.gradle`) so the services stay independent standalone builds. Consequence: the dependency is declared by **module coordinates** `implementation 'com.chefit:distributed-core'` (Gradle substitutes the included build), **not** the `project(':libs:distributed-core')` form the original task text assumed — that form only works in a single multi-project build. A `DistributedCoreWiringTest` (2 tests) in each of recipes/auth/users imports `PubSubChannels`/`NodeInfo` to guard the substitution. All existing suites stayed green after wiring.

**Deferred.** Security-class de-duplication (`JwtService`/`PasswordHasher`/`PasswordPolicy`) is **not** here — see Task 11.

### Task 8 — Implement peer discovery via Redis pub/sub
Create `DiscoveryManager.java` in `distributed-core`. On startup each instance publishes `{"type":"DISCOVER","id":...,"ip":...}` to `chefit:{service}:discovery`. Existing peers respond with `{"type":"HERE","id":...,"ip":...}`. On shutdown publish `{"type":"LEAVE","id":...}`. A dedicated subscriber thread maintains a `ConcurrentHashMap<String, String> peers` (id → ip). Each service wires it up via `@Component` + `@PostConstruct`/`@PreDestroy`.

This replaces `initialize()` + `listenForMulticast()` from `IdServer`. The hardcoded network interface (`"eno1"`) and `MulticastSocket` setup are gone.

### Task 9 — Implement Bully election via Redis pub/sub
Create `ElectionManager.java` in `distributed-core`. Publishes and subscribes on `chefit:{service}:election`. Message types: `ELECTION`, `SUPPRESS`, `COORDINATOR` — same Bully logic as `IdServer.handleElectionMessage()`, `startElection()`, `becomeCoordinator()`. `DiscoveryManager` triggers a new election when a peer leaves and the missing peer was the known coordinator. After election each instance holds an `isCoordinator` boolean that gates replication in Task 10.

### Task 10 — Implement heartbeat + async replication (eventual consistency)
**Heartbeat** — Create `HeartbeatManager.java`. Coordinator publishes `{"type":"HEARTBEAT","id":...}` to `chefit:{service}:heartbeat` every 3 seconds using `@Scheduled(fixedRate = 3000)`. Non-coordinators subscribe; if no heartbeat arrives within 4 seconds they call `ElectionManager.startElection()`.

**Replication** — Create `ReplicationManager.java`. On a write the coordinator:
1. Commits to its own Redis immediately
2. Returns `200 OK` to the gateway **without waiting** ← eventual consistency; replaces the blocking `replicateLock.wait()` from the prototype
3. Publishes the write as a JSON command to `chefit:{service}:replication` asynchronously

Replicas subscribe to the replication channel and apply each received command to their own Redis. There is no ACK and no blocking wait — replicas converge in the background. The prototype's `replicateToBackups()` ACK loop and `acks` set are not carried forward.

### Task 11 — Decide the permanent home for the shared security classes (`JwtService`, `PasswordHasher`, `PasswordPolicy`)
**Status: deferred — decision pending. Task 7 is intentionally primitives-only; this task exists so the security/clustering concerns don't get fused by reflex.** Resolve this *after* Task 7 lands, as its own TDD pass. Recording the full analysis here so the trade-offs aren't re-litigated from scratch later.

**Background.** These three classes are currently duplicated: full versions in `auth` (Task 5 — including issuance), validate/verify copies in `users` (Task 6), and `recipes` will likely need JWT validation once its writes are protected. The hashing scheme (salt + pepper SHA-256, `salt$hex` storage format) and the shared pepper **must stay byte-identical across every copy** or hashes silently stop cross-verifying — a drift bug that's invisible until a login/verify fails. This is the core hazard the decision must address.

**Key asymmetry that shapes everything.** Only `auth` *issues* tokens (`JwtService.issue`) and *creates* password hashes from scratch (registration). Every other service only *validates* tokens (`JwtService.parse`) and *verifies* passwords (change/delete). The surface other services actually need is the validate/verify subset — **not** issuance. A good answer can keep issuance an auth-only concern while sharing only validation/verification.

**Option A — keep security in `auth`** (A1: leave it duplicated as today; A2: make `users`/`recipes` depend on the `auth` module).
- *Pros:* Correct semantic fit (auth code in the auth service). Zero new module / minimal Gradle churn; smallest blast radius. A1 keeps services genuinely independent ("share nothing, even code") — a legitimate microservice stance. Crypto reviewed in one conceptual place.
- *Cons:* A1's duplication is the live hazard above (already 2 copies; recipes makes 3). A2 inverts the dependency graph badly — a *management* service compile-depending on the *authentication* service drags in auth's Google verifier, DAO, controllers, DTOs, and makes auth un-droppable. Doesn't honor the Task 5/6 de-dup intent.

**Option B — move security into `distributed-core`.**
- *Pros:* Reuses the module Task 7 already creates and wires into all three services — low marginal cost. Eliminates duplication (kills the drift hazard). Clean dependency direction (service → shared lib, never service → service).
- *Cons:* **Cohesion violation — the strongest objection.** `distributed-core` means *clustering* (discovery/election/heartbeat/replication); JWT/password crypto is *security*. The two have completely disjoint reasons to change — module-level Single-Responsibility smell. Forces unwanted coupling (any service wanting pub/sub also compiles in crypto, and vice versa). Worst home for an audit surface — crypto buried in a "distributed" lib is easy to overlook.

**Option C — new `security-core` (or `auth-core`) module.**
- *Pros:* Best cohesion — two shared modules each with one job (`distributed-core` = clustering, `security-core` = token + password primitives), each with a single reason to change. Minimal, obviously-named, auditable crypto surface. Right-sized dependencies: a service takes `security-core` iff it touches tokens/passwords and `distributed-core` iff it clusters — independently (e.g. recipes could validate JWTs without inheriting election code). Eliminates duplication. Naturally fits the issue/validate asymmetry.
- *Cons:* Most up-front structure — two new subprojects to wire into the (already risky) multi-project build. Possibly over-engineered for a CS455-scope project; a 4-service + 2-lib build is heavier to reason about. Same regression blast radius as B (touches auth + users wiring), spread across an extra module.

**Weighing / leaning (not yet locked).**
- Lowest risk + microservice independence → **A1**, *but* add a cross-service guardrail test asserting the hash format/pepper contract stays identical, turning the drift con into a caught regression.
- Cleanest architecture, willing to pay structural cost → **C** (the "correct" answer — security and clustering are genuinely different concerns).
- **Avoid B** — it only looks cheap because the module already exists; it permanently fuses two unrelated responsibilities and is the worst home for crypto from an audit standpoint.

**Decision to make in this task:** pick A1 (+ guardrail test) vs. C, then either add the drift-contract test or create `security-core` and migrate `auth`/`users` onto it. Whatever is chosen, issuance stays auth-only; only the validate/verify surface is shared.

---

## Consistency Model Change

| | Prototype (`IdServer`) | This implementation |
|---|---|---|
| **Model** | Sequential consistency | Eventual consistency |
| **Protocol** | Blocking primary-backup | Async fire-and-forget replication |
| **Write flow** | Coordinator waits for ACK from all replicas, then responds | Coordinator commits locally, responds immediately, replicates async |
| **Read staleness** | Reads always current (all replicas in sync before ack) | Replica may briefly return stale data after a write |
| **Transport** | UDP multicast + Java object serialization | Redis pub/sub + JSON |
| **Failure on replica down** | Write blocked if any replica fails to ACK in time | Write succeeds; lagging replica catches up when it recovers |

---

## Target Architecture

```
Browser
  └─► Gateway :8080
        ├─► /items/**  → Recipes service :8081  ─► Redis (data + pub/sub)
        ├─► /auth/**   → Auth service    :8082  ─► Redis (data + pub/sub)
        └─► /users/**  → Users service   :8083  ─► Redis (data + pub/sub)

Each service cluster (example: two recipes instances):
  :8081 instance A  ──pub/sub──►  chefit:recipes:*  ◄──pub/sub──  :8081 instance B
  Bully election picks coordinator → coordinator handles writes → async replication to peers
```
