# distributed-core

Shared coordination primitives for the ChefIt microservices. This is a plain Java
**library** (no Spring, no `bootRun`, no `main`) consumed by `recipes`, `auth`, and `users`.
It exists so the cluster logic in Tasks 8–10 (peer discovery, Bully election, heartbeat,
async replication) is written **once** instead of copy-pasted into every service.

Package: `com.chefit.distributed`.

## What's in it

| Class | Responsibility |
|---|---|
| `PubSubChannels` | The single source of truth for Redis pub/sub channel names — `chefit:{service}:{purpose}` for `discovery` / `election` / `heartbeat` / `replication`. A publisher and a subscriber in different processes must compute the identical string, so this is centralized and guarded (null/blank service → `IllegalArgumentException`). |
| `NodeInfo` | This instance's identity, embedded into coordination messages: `record(id, ip, service)`. `create(service)` derives `id` from the JVM PID (`ProcessHandle.current().pid()`, as a `String`) and `ip` from the local host address (loopback fallback if it can't resolve). |
| `RedisConnectionFactory` | Hands out **two kinds** of Jedis connections from one `host:port`: a shared pooled `dataPool()` for ordinary commands, and a fresh, caller-owned `newPubSubConnection()` for subscribers. The split is load-bearing — a `SUBSCRIBE` blocks its thread for the life of the subscription and can never be returned to a pool. |

Deliberately **not** included: the legacy `ServerMessage` / `Request` / `Listener` / `Utility`
classes — they served UDP-socket Java serialization, replaced here by plain JSON over Redis
pub/sub. The shared security classes (`JwtService` / `PasswordHasher` / `PasswordPolicy`) are
also **not** here — their home is an open decision tracked as Task 11 in `TODO.md`.

## How services consume it

A Gradle **composite build**, not a multi-project build. Each service keeps its own
independent build and pulls this library in:

```gradle
// services/<name>/settings.gradle
includeBuild '../../libs/distributed-core'

// services/<name>/build.gradle
dependencies {
    implementation 'com.chefit:distributed-core'   // substituted by the included build
}
```

Gradle substitutes the `com.chefit:distributed-core` dependency with this source build and
compiles it on demand. Note this uses **module coordinates**, not the
`project(':libs:distributed-core')` form (that only works inside a single root multi-project
build, which ChefIt deliberately does not use).

## Building & testing

Standalone — runs in isolation from the services:

```bash
cd libs/distributed-core
./gradlew test     # 26 tests
./gradlew build    # compile + test + jar
```

- **Unit tests** (`PubSubChannelsTest`, `NodeInfoTest`, `RedisConnectionFactoryTest`) need no Redis.
- **Integration tests** (`RedisConnectionFactoryIntegrationTest`) require a live Redis on
  `localhost:6379` — start it from the repo root with `docker compose up -d`. They prove the
  data/pub-sub split end to end (a SET/GET round-trip, and a subscriber on a dedicated
  connection receiving a message published over a pooled connection).

## Build details

Standalone `java-library` build with its own Gradle wrapper. Dependency versions are pinned
here (jedis 5.2.0, jackson-databind 2.18.2, JUnit 5.11.4) because — unlike the services —
there is no Spring dependency-management BOM to supply them.
