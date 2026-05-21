# ChefIt Gateway

Spring Cloud Gateway (WebFlux). Receives all requests from the frontend and routes them to the appropriate microservice. The frontend should never call a service directly.

## Running

```bash
cd gateway/gateway
./gradlew bootRun    # starts on :8080
./gradlew build      # compile + test
```

## Routes

| Path | Forwards to |
|---|---|
| `/items/**` | Recipes service `:8081` |
| `/auth/**` | Auth service `:8082` |
| `/users/**` | Users service `:8083` |

Routes are configured in `gateway/src/main/resources/application.properties`.

## CORS

`CorsConfig.java` allows requests from `localhost:5173` (Vite dev server) and `localhost:3000`. Update allowed origins there when deploying to other environments.

## Ports

| Service | Port |
|---|---|
| Gateway | 8080 |
| Recipes | 8081 |
| Auth | 8082 |
| Users | 8083 |
| Redis | 6379 |
