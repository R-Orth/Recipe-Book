# ChefIt Services

Three Spring Boot microservices. All require Redis to be running before `bootRun`.

## Redis

Start Redis from the repo root before running any service:
```bash
docker compose up -d
```
Verify: `docker exec chefit-redis redis-cli ping` → should return `PONG`.

## Services

| Service | Port | Status | Description |
|---|---|---|---|
| `recipes` | 8081 | Complete | Recipe CRUD backed by Redis |
| `auth` | 8082 | Scaffolded | User authentication |
| `users` | 8083 | Scaffolded | User management |

## Running a service

```bash
cd services/<name>
./gradlew bootRun          # start on the assigned port
./gradlew build -x test    # build JAR without running tests
```

## Testing

Each service has two test tiers. See the individual service README for details.

**Unit tests** — no Redis needed:
```bash
./gradlew test
```

**Unit + integration tests** — Redis must be running:
```bash
./gradlew build
```

## Configuration

Each service reads `redis.host` and `redis.port` from its `application.properties` (defaults `localhost:6379`). Logging goes to `logs/<service>.log` relative to the service directory.

## Distributed system (planned — Tasks 7–10)

Once implemented, each service will:
- Discover peer instances via Redis pub/sub on `chefit:<service>:discovery`
- Elect a coordinator via the Bully algorithm on `chefit:<service>:election`
- Replicate writes asynchronously on `chefit:<service>:replication` (eventual consistency)
- Send/receive heartbeats on `chefit:<service>:heartbeat` to detect coordinator failure
