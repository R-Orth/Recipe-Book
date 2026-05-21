# Recipes Service

Spring Boot 4.x service running on `:8081`. Handles recipe CRUD backed by Redis.

## Running

Redis must be running first (`docker compose up -d` from repo root).

```bash
./gradlew bootRun       # start on :8081
./gradlew build         # compile, run all tests, build JAR
./gradlew build -x test # build JAR without tests
```

## Endpoints

All routes are relative to `:8081`. In production these are accessed via the gateway at `:8080`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/items` | List all recipes |
| `GET` | `/items/{id}` | Get one recipe by id |
| `PUT` | `/items` | Create or update a recipe |
| `DELETE` | `/items/{id}` | Delete a recipe |

### Request / response shape

```json
{
  "id": "uuid",
  "name": "Banana Bread",
  "time": "60",
  "servings": "8",
  "ingredients": [
    { "name": "Flour", "amount": "2", "measurement": "Cup" }
  ],
  "steps": ["Mash bananas", "Mix ingredients", "Bake at 350F for 60 minutes"]
}
```

`id` is optional on `PUT`. If omitted, the service generates a UUID. If provided, that id is used as-is — this allows the replication layer (Task 10) to replay writes on replicas with the same id.

## Redis data layout

| Key | Type | Contents |
|---|---|---|
| `recipe:{id}` | Hash | All recipe fields; `ingredients` and `steps` are JSON strings |
| `recipe:index` | Set | All known recipe ids — used by `findAll()` instead of `KEYS *` |

## Tests

### Unit tests (no Redis required)

```bash
./gradlew test --tests "com.chefit.recipes.dao.RecipeDAOTest"
./gradlew test --tests "com.chefit.recipes.controller.RecipesControllerTest"
./gradlew test --tests "com.chefit.recipes.RecipesApplicationTests"
```

| Class | What it tests |
|---|---|
| `RecipeDAOTest` | Redis command calls, UUID generation, id preservation for replication replay, deserialization |
| `RecipesControllerTest` | HTTP status codes, JSON response shape, 404 handling (`@WebMvcTest` — no Spring Boot full context) |
| `RecipesApplicationTests` | Spring context loads without a live Redis |

### Integration tests (Redis must be running)

```bash
docker compose up -d                  # from repo root
./gradlew test --tests "com.chefit.recipes.dao.RecipeDAOIntegrationTest"
```

| Test | What it verifies |
|---|---|
| `save_andFindById_roundTrip` | Full write + read against real Redis |
| `save_withIngredients_deserializesCorrectly` | Ingredient list survives JSON serialization round-trip |
| `findAll_returnsAllSavedRecipes` | `recipe:index` set is maintained correctly |
| `findAll_returnsEmptyListOnFreshRedis` | Clean state before each test |
| `delete_removesHashAndIndex` | Both `recipe:{id}` hash and `recipe:index` entry removed |
| `delete_returnsFalseForNonExistentId` | Safe to delete a missing id |
| `delete_doesNotAffectOtherRecipes` | Deleting one recipe leaves others intact |
| `save_withExistingId_overwritesForReplication` | Saving with an existing id overwrites — idempotent replay safe |

### Run all tests

```bash
./gradlew build    # requires Redis
```

## Spring Boot 4.x notes

- `@WebMvcTest` is in `org.springframework.boot.webmvc.test.autoconfigure` (moved from `org.springframework.boot.test.autoconfigure.web.servlet` in 3.x)
- `ObjectMapper` is not auto-configured as a Spring bean by `spring-boot-starter-webmvc` — `RecipeDAO` instantiates it directly
- Both `spring-boot-starter-test` and `spring-boot-starter-webmvc-test` are required as test dependencies to get `@WebMvcTest` + full Mockito support
