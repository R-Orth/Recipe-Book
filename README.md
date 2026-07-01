# ChefIt — Recipe Book (CS455 Distributed Systems)

## Getting Started — Microservices Stack

### Prerequisites
- Java 25, Node.js 20+
- Docker Desktop (for Redis)

### 1. Start Redis
From the repo root:
```bash
docker compose up -d
```
Redis listens on `localhost:6379`. RedisInsight (browser GUI) at `http://localhost:8001`.

### 2. Start the gateway and services
Each in its own terminal:
```bash
cd gateway/gateway  &&  ./gradlew bootRun   # :8080 — API gateway
cd services/recipes &&  ./gradlew bootRun   # :8081 — recipe CRUD
cd services/auth    &&  ./gradlew bootRun   # :8082 — authentication
cd services/users   &&  ./gradlew bootRun   # :8083 — user management
```

> **Shared library — no separate step.** The three services share coordination code from
> `libs/distributed-core` via a Gradle **composite build** (`includeBuild` in each service's
> `settings.gradle`). The first `bootRun`/`test` of any service compiles `distributed-core`
> from source automatically — you'll see `:distributed-core:compileJava` / `:distributed-core:jar`
> in the task list. There is no publish step and no root multi-project build; each service
> stays an independent Gradle build. See `libs/distributed-core/README.md`.

### 3. Start the frontend
```bash
cd frontend/ChefIt
npm install
npm run dev   # http://localhost:5173
```

### Architecture
```
Browser
  └─► Gateway :8080
        ├─► /items/**  → recipes :8081 ─┐
        ├─► /auth/**   → auth    :8082 ─┼─► Redis :6379  (data + pub/sub)
        └─► /users/**  → users   :8083 ─┘
                              │
        each service compiles in ──► libs/distributed-core
        (PubSubChannels · NodeInfo · RedisConnectionFactory)
```
`distributed-core` is a standalone Gradle library holding the cross-service coordination
primitives (Redis pub/sub channel names, node identity, and the data/pub-sub connection
factory) that Tasks 8–10 build on. It is pulled into each service as a composite build.

### Running tests
```bash
# Any service (from services/<name>/)
./gradlew test          # unit tests only (no Redis needed)
./gradlew build         # unit + integration tests (Redis must be running)

# Shared library (from libs/distributed-core/)
./gradlew test          # 26 tests; 2 integration tests need Redis live

# Gateway (from gateway/gateway/) — no Redis needed
./gradlew test
```
Each service also carries a `DistributedCoreWiringTest` that imports the shared classes and
fails fast if the composite-build wiring breaks. The full per-service test breakdown and the
build plan live in `TODO.md`.

---

# Project 3: Identity Server (Phase 2)

* Author: Ryan Orth & Adam Taylor
* Class: CS455 [Distributed Systems] Section #001


# 3 Late Days Please


## Overview

An Identity Server that allows a client to use RMI to access a Jedis database that stores the information about the users including login information, hashed passwords, and other analytical data. A coordinator server processes requests from the client and replicates its actions to the back up servers.

## Building the code

We use a make file to compile our programs, you run it with the following:

~~~ 
    ./build.sh
~~~

Before you start the primary server you need to start the redis client on your local machine with:
~~~ 
    redis-server
~~~

To start the Identity server:


:(Linux & Mac)
~~~
    java -cp ".:./lib/*" server/IdServer [--numport <registry port>] [--verbose] 
~~~

:(Windows)
~~~
    java -cp ".;./lib/*" server/IdServer [--numport <registry port>] [--verbose] 
~~~

To start and use the client, use this command-line argument
~~~
    ./run-client.sh --server <serverhost> [--numport <port#>] <query>
~~~