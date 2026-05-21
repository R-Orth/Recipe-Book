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

### Task 3 — Configure Spring Cloud Gateway routes
Remove the stub `RecipesController` from the gateway — it conflicts with routing on the same paths. Add to `gateway/application.properties`:
```properties
spring.cloud.gateway.routes[0].id=recipes
spring.cloud.gateway.routes[0].uri=http://localhost:8081
spring.cloud.gateway.routes[0].predicates[0]=Path=/items/**
```
Add placeholder routes for auth (`/auth/**` → 8082) and users (`/users/**` → 8083) so the gateway owns all routing from day one.

### Task 4 — Switch the frontend from AWS to the gateway
In `src/utils/api.js`: make `GATEWAY_URL` the primary URL in all four functions (`fetchAllRecipes`, `fetchRecipeById`, `createRecipe`, `deleteRecipeById`), demote `AWS_URL` to a commented-out fallback. Remove the `pingGateway` fire-and-forget calls — the gateway *is* the call now.

### Task 5 — Add Jedis to `services/auth` and implement `UserDAO` + `AuthController`
Same Jedis pattern as Task 1. `UserDAO` stores: `login`, `uuid`, `hashedPassword`, `realname`, `ip`, `createDate`, `modifyDate` as Redis hash fields. `AuthController` at `/auth`:
- `POST /auth/register` — hashes password (SHA-256), stores via DAO
- `POST /auth/login` — verifies hashed password
- `GET /auth/{login}` — returns user fields minus password

### Task 6 — Add Jedis to `services/users` and implement `UsersController`
`UsersController` at `/users` wraps the same Redis user hashes as auth but exposes the management interface: `GET /users` (all users), `GET /users/{id}` (by UUID), `PUT /users/{id}` (modify), `DELETE /users/{id}` (delete with password verify). At this point all three services back the gateway and the frontend works end-to-end without touching AWS.

### Task 7 — Extract a shared `distributed-core` Gradle subproject
Tasks 8–10 repeat across all three services. Create `libs/distributed-core/` as a Gradle subproject containing:
- `PubSubChannels.java` — channel name constants (`chefit:{service}:discovery`, `:election`, `:heartbeat`, `:replication`)
- `RedisConnectionFactory.java` — creates separate Jedis connections for data and pub/sub (a subscriber connection blocks its thread and cannot be reused for commands)
- `NodeInfo.java` — holds this instance's id (`ProcessHandle.current().pid()`), IP, and service name

Do **not** port `ServerMessage.java`, `Request.java`, `Listener.java`, or `Utility.java` — those existed to serialize Java objects over UDP sockets. All inter-node messages are now plain JSON strings over Redis pub/sub; Spring's `ObjectMapper` handles serialization.

Include in recipes/auth/users `build.gradle` via `implementation project(':libs:distributed-core')`.

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
