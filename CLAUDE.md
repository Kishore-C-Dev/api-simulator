# CLAUDE.md - AI-Focused Technical Documentation

## Project Overview

**API Simulator**: WireMock-backed API simulator with web UI for creating/managing mock REST/GraphQL APIs with configurable delays, chaos engineering, and workspace isolation.

**Core Functionality:**
- Dynamic API simulation (WireMock engine) with MongoDB persistence
- Multi-user RBAC (Admin/User) with workspace/namespace isolation
- Web UI (htmx/TailwindCSS) for mapping management
- Advanced pattern matching (JSONPath, XPath, regex), chaos engineering, import/export

## Technology Stack

**Languages:** Java 17, JavaScript ES6+, HTML5
**Backend:** Spring Boot 3.2.0 (web, thymeleaf, data-mongodb, validation, actuator), WireMock 3.3.1, Jackson, JSONPath, Commons Lang3
**Frontend:** htmx, TailwindCSS 3.x, Thymeleaf
**Database:** MongoDB 7.0+
**Build/Deploy:** Maven 3.6+, Docker, Docker Compose
**Testing:** JUnit 5, Mockito, AssertJ, Embedded MongoDB 4.11.0

## Architecture & Design

### High-Level Architecture

**Layered Monolithic Architecture** with clean separation of concerns:

```
┌─────────────────────────────────────────────────────┐
│              Web Browser (Client)                    │
└─────────────────────┬───────────────────────────────┘
                      │ HTTP (htmx)
┌─────────────────────▼───────────────────────────────┐
│          Spring Boot Application (Port 8080)         │
│  ┌─────────────────────────────────────────────┐   │
│  │    Controllers (Web, Admin, Auth, Test)     │   │
│  └─────────────────┬───────────────────────────┘   │
│  ┌─────────────────▼───────────────────────────┐   │
│  │  Services (Mapping, User, WireMock, Delay)  │   │
│  └─────────────────┬───────────────────────────┘   │
│  ┌─────────────────▼───────────────────────────┐   │
│  │     Repositories (MongoDB Data Access)       │   │
│  └─────────────────┬───────────────────────────┘   │
└────────────────────┼───────────────────────────────┘
                     │
         ┌───────────┴──────────┐
         │                      │
┌────────▼────────┐   ┌─────────▼──────────┐
│  MongoDB (27017) │   │ WireMock (9999)   │
│   - mappings     │   │  Simulated APIs   │
│   - userProfiles │   └───────────────────┘
│   - namespaces   │
└─────────────────┘
```

### Design Patterns & Architectural Decisions

**Patterns:** Repository (Spring Data), Service Layer, DI, Builder (RequestMapping), Transformer (WireMock), Strategy (pattern matching), Command (data seeding)

**Key Decisions:**
- Embedded WireMock server for simplified deployment
- MongoDB for flexible schema and complex document storage
- htmx for dynamic UI without heavy JS frameworks
- Namespace-based isolation on all data models
- Soft delete for audit trail
- Priority-based routing (lower number = higher precedence)
- Session-based authentication

### Data Flow and Request Lifecycle

#### Mock API Request Flow (Port 9999)
1. Client sends HTTP request to WireMock endpoint (e.g., `GET http://localhost:9999/hello`)
2. WireMock evaluates request against loaded stub mappings (priority-ordered)
3. First matching stub is selected based on path, method, headers, query params, and body patterns
4. Custom transformers apply (ConditionalResponseTransformer, GraphQLResponseTransformer)
5. Delay service introduces fixed/variable latency if configured
6. Chaos engineering randomly injects errors based on error rate percentage
7. Response body undergoes Handlebars template processing (if enabled)
8. Final response returned to client

#### Admin UI Request Flow (Port 8080)
1. User accesses web UI (requires authentication if enabled)
2. Browser sends htmx-powered request to Spring Boot controller
3. SessionManager validates user session and namespace access
4. Controller delegates to service layer for business logic
5. Service layer interacts with MongoDB repositories
6. For mapping changes, WireMockMappingService reloads stubs into WireMock
7. Controller returns HTML fragment or full page via Thymeleaf
8. htmx swaps content into DOM without full page reload

## Project Structure

```
api-simulator/
├── backend/                          # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/simulator/
│   │   │   │   ├── config/           # Configuration classes
│   │   │   │   │   └── WireMockConfig.java
│   │   │   │   ├── controller/       # HTTP request handlers
│   │   │   │   │   ├── AdminController.java    # Admin REST API & htmx endpoints
│   │   │   │   │   ├── AuthController.java     # Login/logout/session
│   │   │   │   │   ├── TestController.java     # Test endpoints
│   │   │   │   │   └── WebController.java      # UI controllers
│   │   │   │   ├── model/            # Domain models
│   │   │   │   │   ├── Dataset.java            # Legacy dataset model
│   │   │   │   │   ├── EndpointType.java       # REST/GraphQL enum
│   │   │   │   │   ├── GraphQLSpec.java        # GraphQL schema spec
│   │   │   │   │   ├── Namespace.java          # Workspace/team model
│   │   │   │   │   ├── RequestMapping.java     # Core mapping model
│   │   │   │   │   └── UserProfile.java        # User account model
│   │   │   │   ├── repository/       # Data access layer
│   │   │   │   │   ├── DatasetRepository.java
│   │   │   │   │   ├── NamespaceRepository.java
│   │   │   │   │   ├── RequestMappingRepository.java
│   │   │   │   │   └── UserProfileRepository.java
│   │   │   │   ├── service/          # Business logic
│   │   │   │   │   ├── ConditionalResponseService.java
│   │   │   │   │   ├── DataSeederService.java  # Seed data on startup
│   │   │   │   │   ├── DelayService.java       # Latency simulation
│   │   │   │   │   ├── DemoDataSeeder.java     # Demo users/namespaces
│   │   │   │   │   ├── GraphQLParserService.java
│   │   │   │   │   ├── GraphQLResponseGenerator.java
│   │   │   │   │   ├── MappingService.java     # Core mapping CRUD
│   │   │   │   │   ├── RequestMatchingService.java
│   │   │   │   │   ├── SessionManager.java     # User session handling
│   │   │   │   │   ├── UserService.java        # User CRUD
│   │   │   │   │   └── WireMockMappingService.java  # WireMock integration
│   │   │   │   ├── wiremock/         # WireMock extensions
│   │   │   │   │   ├── ConditionalResponseTransformer.java
│   │   │   │   │   └── GraphQLResponseTransformer.java
│   │   │   │   └── ApiSimulatorApplication.java  # Main entry point
│   │   │   └── resources/
│   │   │       ├── application.yml   # Application configuration
│   │   │       ├── static/           # CSS and static assets
│   │   │       │   └── css/
│   │   │       └── templates/        # Thymeleaf HTML templates
│   │   │           ├── dashboard.html
│   │   │           ├── graphql-mapping-form.html
│   │   │           ├── import-export.html
│   │   │           ├── layout.html
│   │   │           ├── login.html
│   │   │           ├── mapping-form.html
│   │   │           └── test-panel.html
│   │   └── test/                     # Unit and integration tests
│   │       └── java/com/simulator/
│   │           ├── controller/
│   │           ├── repository/
│   │           └── service/
│   ├── Dockerfile                    # Container build instructions
│   ├── pom.xml                       # Maven dependencies
│   └── target/                       # Compiled artifacts (gitignored)
├── docker-compose.yml                # Multi-container orchestration
├── build.sh                          # Build and run script
├── .env.example                      # Environment variable template
├── .gitignore                        # Git ignore rules
├── README.md                         # User-facing documentation
└── CLAUDE.md                         # This file (AI documentation)
```

**Key Directories:** config (Spring/WireMock setup), controller (HTTP/htmx endpoints), model (MongoDB documents), repository (data access), service (business logic), wiremock (transformers), resources/templates (Thymeleaf), resources/static (CSS), test (unit/integration)

**Key Files:** ApiSimulatorApplication.java (entry point), WireMockConfig.java (WireMock setup), MappingService.java (CRUD), WireMockMappingService.java (MongoDB→WireMock), RequestMapping.java (core model), application.yml (config), docker-compose.yml (orchestration), build.sh (build script)

## Prerequisites

**Software:** JDK 17+, Maven 3.6+, MongoDB 7.0+ (local) OR Docker 20.10+ & Docker Compose 2.0+
**System:** 2-4GB RAM, ~1GB disk, ports 8080/9999/27017 available
**Services:** MongoDB URI (local/remote), no external APIs required

## Setup Instructions

**Docker Compose (Recommended):**
```bash
git clone <repo> && cd api-simulator
cd backend && mvn clean package -DskipTests && cd ..
docker-compose up --build -d
# Access: http://localhost:8080 (admin/admin123 or demo/demo123)
```

**Local Development:**
```bash
# Start MongoDB: brew services start mongodb-community OR docker run -d -p 27017:27017 mongo:7.0
git clone <repo> && cd api-simulator/backend
mvn spring-boot:run
# Access: http://localhost:8080 (UI), http://localhost:9999 (WireMock)
```

**Environment:** Copy `.env.example` to `.env` for custom config

**Database:** Auto-created on startup - `simulator` DB with `mappings`, `userProfiles`, `namespaces` collections. Auto-indexed.

**Seed Data:** Auto-seeded if empty - 3 demo mappings (GET /hello, POST /orders, GET /flaky), admin/demo users with namespaces

## Configuration

### Environment Variables

| Variable | Default | Description | Required |
|----------|---------|-------------|----------|
| `MONGO_URI` | `mongodb://localhost:27017/simulator` | MongoDB connection string | No |
| `ACTIVE_DATASET` | `default` | Default namespace/workspace (legacy support) | No |
| `SERVER_PORT` | `8080` | Spring Boot HTTP port | No |
| `AUTH_ENABLED` | `true` | Enable authentication and user management | No |

### Application Properties (application.yml)

Located at: `backend/src/main/resources/application.yml`

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: api-simulator
  data:
    mongodb:
      uri: ${MONGO_URI:mongodb://localhost:27017/simulator}
  thymeleaf:
    cache: false
    prefix: classpath:/templates/
    suffix: .html

logging:
  level:
    com.simulator: DEBUG
    com.github.tomakehurst.wiremock: INFO
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized

simulator:
  active-dataset: ${ACTIVE_DATASET:default}
  auth:
    enabled: ${AUTH_ENABLED:false}
  wiremock:
    port: 9999
    admin-port: 9998
```

**Config Files:** pom.xml (Maven deps), docker-compose.yml (containers/networking), Dockerfile (JAR build)

**Security (Current):** Simple password hashing, session cookies, no encryption at rest
**Production Recommendations:** BCrypt/Argon2, HTTPS/TLS, secrets management (Vault/AWS), MongoDB auth/encryption

## Development Workflow

**Run Locally:** `mvn spring-boot:run` OR `java -jar target/api-simulator-1.0.0.jar` OR `./build.sh`

**Hot Reload:** Add spring-boot-devtools to pom.xml; Thymeleaf auto-reloads with `cache=false`

**Dev Database:** Docker MongoDB (`docker run -d -p 27017:27017 mongo:7.0`), local install, or Docker Compose

**Common Commands:**
```bash
mvn clean package -DskipTests  # Build
mvn test                        # Test
mvn spring-boot:run -Dspring-boot.run.profiles=docker  # Run with profile
docker-compose logs -f backend  # Logs
docker-compose restart          # Restart
docker-compose down [-v]        # Stop [with volume deletion]
```

## Code Organization

**Naming:** Classes: `*Controller`, `*Service`, `*Repository`, nouns (models), `*Transformer`; Variables: camelCase, UPPER_SNAKE_CASE (constants), kebab-case (URLs/CSS)

**Packages:** config, controller, model, repository, service, wiremock

**File Structure:** Controllers (dependencies → GET → POST → PUT/DELETE), Services (logger → deps → public → private), Repositories (custom queries)

**Imports:** Ordered (Java std → 3rd party → internal), no wildcards except static

**Style:** 4 spaces, max 120 chars, K&R braces, JavaDoc public methods, SLF4J logging

## Key Components/Modules

**Main Entry:** `ApiSimulatorApplication.java` - Spring Boot bootstrapping, component scanning, auto-config

### Core Components

**1. MappingService:** CRUD for RequestMapping, namespace retrieval, search/pagination, WireMock reload. Methods: `getAllMappings`, `searchMappings`, `saveMapping`, `deleteMapping`, `reloadWireMockMappings`. Uses: RequestMappingRepository, WireMockMappingService.

**2. WireMockMappingService:** Converts MongoDB RequestMapping → WireMock StubMapping, loads stubs, pattern translation (JSONPath/Regex/XPath), priority-based ordering. Methods: `loadMappings`, `convertToStubMapping`. Algorithms: priority sorting, pattern conversion, delay/chaos config.

**3. WireMockConfig:** Creates WireMock server bean, configures ports (9999/9998), enables Handlebars templating, registers transformers (ConditionalResponse, GraphQL).

**4. ConditionalResponseTransformer:** Request ID-based response variants - checks conditional flag → extracts request ID from header → matches variant → overrides response.

**5. SessionManager:** Session creation/validation, namespace tracking, workspace switching. Methods: `createSession`, `getCurrentUser`, `getCurrentNamespace`, `switchNamespace`.

**6. RequestMapping Model:** Central API config model with nested classes: `RequestSpec` (method/path/headers/params/patterns), `ResponseSpec` (status/headers/body/templating/conditional), `DelaySpec` (fixed/variable/error), `BodyPattern` (JSONPath/XPath/Regex/Contains/Exact), `ParameterPattern`, `ConditionalResponses`.

**Initialization:** Spring Boot start → Component scan → WireMockConfig creates server (9999) → MongoDB connect → DataSeederService seeds → DemoDataSeeder creates users/namespaces → WireMock loads mappings → Server ready (8080/9999)

## Database Schema

**MongoDB 7.0+** with Spring Data MongoDB 3.x

### Collections

#### 1. `mappings` Collection

**Document Structure:**
```javascript
{
  "_id": "uuid-string",
  "name": "Mapping Name",
  "namespace": "workspace-name",
  "priority": 5,
  "endpointType": "REST",  // or "GRAPHQL"
  "enabled": true,
  "tags": ["tag1", "tag2"],
  "createdAt": ISODate("2024-01-01T12:00:00Z"),
  "updatedAt": ISODate("2024-01-01T12:00:00Z"),
  "deleted": false,
  "request": {
    "method": "POST",
    "path": "/api/orders",
    "pathPattern": {
      "matchType": "EXACT",  // or REGEX, WILDCARD
      "pattern": "/api/orders",
      "ignoreCase": false
    },
    "headers": {
      "Content-Type": "application/json"
    },
    "headerPatterns": {
      "X-Request-ID": {
        "matchType": "REGEX",
        "pattern": "^[a-f0-9-]{36}$",
        "ignoreCase": false
      }
    },
    "queryParams": {
      "status": "active"
    },
    "queryParamPatterns": {
      "id": {
        "matchType": "EXISTS"
      }
    },
    "bodyPatterns": [
      {
        "matchType": "JSONPATH",
        "expr": "$.account_number",
        "expected": "1234567890",
        "ignoreCase": false
      }
    ]
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "{\"id\": \"{{randomValue type='UUID'}}\"}",
    "templatingEnabled": true,
    "conditionalResponses": {
      "enabled": true,
      "requestIdHeader": "X-Request-ID",
      "requestIdMappings": [
        {
          "requestId": "test-id-123",
          "status": 400,
          "body": "{\"error\": \"Invalid request\"}",
          "headers": {}
        }
      ]
    },
    "graphQLResponse": {
      "data": {},
      "errors": [],
      "extensions": {}
    }
  },
  "delays": {
    "mode": "variable",  // or "fixed"
    "fixedMs": 200,
    "variableMinMs": 100,
    "variableMaxMs": 500,
    "errorRatePercent": 15,
    "errorResponse": {
      "status": 500,
      "body": "{\"error\": \"Service unavailable\"}"
    }
  },
  "graphQLSpec": {
    "schema": "type Query { ... }",
    "operationType": "query",
    "operationName": "getUser"
  }
}
```

**Indexes:**
- `namespace` (non-unique)
- `enabled` (non-unique)
- Compound index: `{namespace: 1, enabled: 1, priority: -1}`

#### 2. `userProfiles` Collection

**Document Structure:**
```javascript
{
  "_id": "uuid-string",
  "userId": "admin",  // UNIQUE
  "email": "admin@example.com",
  "firstName": "Admin",
  "lastName": "User",
  "passwordHash": "hashed-password-string",
  "namespaces": ["default", "demo"],
  "defaultNamespace": "default",
  "createdAt": ISODate("2024-01-01T12:00:00Z"),
  "lastLogin": ISODate("2024-01-01T12:00:00Z"),
  "active": true,
  "deleted": false
}
```

**Indexes:**
- `userId` (unique)
- `email` (non-unique)

#### 3. `namespaces` Collection

**Document Structure:**
```javascript
{
  "_id": "uuid-string",
  "name": "team-alpha",  // UNIQUE
  "displayName": "Team Alpha",
  "description": "Development team workspace",
  "members": ["admin", "user1", "user2"],
  "owner": "admin",
  "createdAt": ISODate("2024-01-01T12:00:00Z"),
  "active": true,
  "deleted": false
}
```

**Indexes:**
- `name` (unique)

**Relationships (logical, not enforced):**
- `RequestMapping.namespace` → `Namespace.name` (many-to-one)
- `UserProfile.namespaces[]` ↔ `Namespace.members[]` (many-to-many)
- `Namespace.owner` → `UserProfile.userId` (one-to-one)

**Migrations:** No formal framework - flexible schema. New fields auto-persist, missing fields return null. Breaking changes use MongoDB shell or CommandLineRunner scripts.

## API Documentation

**Base URLs:** Admin/Web UI (http://localhost:8080), WireMock API (http://localhost:9999). No versioning (v1 assumed).

**Auth:** Session-based - `POST /login` (form), session cookie, `POST /logout`.
**Authorization:** Admin (full access), User (assigned namespaces only), Public (`/login`, `/test/echo`, WireMock:9999)

### Main Endpoints

#### Admin Mapping API

**Base Path:** `/admin`

| Endpoint | Method | Description | Request Body | Response | Auth Required |
|----------|--------|-------------|--------------|----------|---------------|
| `/admin/mappings` | GET | List all mappings (paginated) | - | `Page<RequestMapping>` | Yes |
| `/admin/mappings/{id}` | GET | Get mapping by ID | - | `RequestMapping` | Yes |
| `/admin/mappings` | POST | Create new mapping | `RequestMapping` JSON | `RequestMapping` | Yes |
| `/admin/mappings/{id}` | PUT | Update existing mapping | `RequestMapping` JSON | `RequestMapping` | Yes |
| `/admin/mappings/{id}` | DELETE | Delete mapping (soft delete) | - | 204 No Content | Yes |
| `/admin/mappings/refresh` | POST | Reload WireMock stubs | - | Success message | Yes |
| `/admin/import` | POST | Import mappings from JSON | Multipart file | Success message | Yes |
| `/admin/export` | GET | Export mappings as JSON | - | JSON file download | Yes |

**Query Parameters for GET `/admin/mappings`:**
- `page` (int, default 0): Page number
- `size` (int, default 10): Page size
- `sortBy` (string, default "priority"): Sort field
- `sortDir` (string, default "desc"): Sort direction (asc/desc)
- `search` (string, optional): Search query

#### Authentication API

| Endpoint | Method | Description | Request Body | Response | Auth Required |
|----------|--------|-------------|--------------|----------|---------------|
| `/login` | POST | User login | Form: `username`, `password`, `workspace` | Redirect to dashboard | No |
| `/logout` | POST | User logout | - | Redirect to login | Yes |
| `/auth/current` | GET | Get current user session info | - | User and namespace info | Yes |
| `/auth/switch-workspace` | POST | Switch active workspace | Form: `workspace` | Success message | Yes |

#### User Management API (Admin Only)

| Endpoint | Method | Description | Request Body | Response | Auth Required |
|----------|--------|-------------|--------------|----------|---------------|
| `/admin/users` | GET | List all users | - | `List<UserProfile>` | Admin |
| `/admin/users` | POST | Create new user | `UserProfile` JSON | `UserProfile` | Admin |
| `/admin/users/{id}` | PUT | Update user | `UserProfile` JSON | `UserProfile` | Admin |
| `/admin/users/{id}` | DELETE | Delete user (soft delete) | - | 204 No Content | Admin |

#### Workspace Management API (Admin Only)

| Endpoint | Method | Description | Request Body | Response | Auth Required |
|----------|--------|-------------|--------------|----------|---------------|
| `/admin/workspaces` | GET | List all workspaces | - | `List<Namespace>` | Admin |
| `/admin/workspaces` | POST | Create new workspace | `Namespace` JSON | `Namespace` | Admin |
| `/admin/workspaces/{id}` | PUT | Update workspace | `Namespace` JSON | `Namespace` | Admin |
| `/admin/workspaces/{id}` | DELETE | Delete workspace (soft delete) | - | 204 No Content | Admin |

#### Test API

| Endpoint | Method | Description | Request Body | Response | Auth Required |
|----------|--------|-------------|--------------|----------|---------------|
| `/test/echo` | POST | Echo JSON request with metadata | Any JSON | Echo response | No |

### Request/Response Examples

#### Create Mapping Example

**Request:**
```bash
curl -X POST http://localhost:8080/admin/mappings \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Get User by ID",
    "priority": 5,
    "enabled": true,
    "tags": ["users", "get"],
    "request": {
      "method": "GET",
      "path": "/api/users/123",
      "headers": {
        "Authorization": "Bearer token"
      }
    },
    "response": {
      "status": 200,
      "headers": {
        "Content-Type": "application/json"
      },
      "body": "{\"id\": 123, \"name\": \"John Doe\"}",
      "templatingEnabled": false
    },
    "delays": {
      "mode": "fixed",
      "fixedMs": 100,
      "errorRatePercent": 0
    }
  }'
```

**Response:**
```json
{
  "id": "generated-uuid",
  "name": "Get User by ID",
  "namespace": "default",
  "priority": 5,
  "enabled": true,
  "createdAt": "2024-01-01T12:00:00Z",
  "updatedAt": "2024-01-01T12:00:00Z",
  "request": { ... },
  "response": { ... },
  "delays": { ... }
}
```

#### Login Example

**Request:**
```bash
curl -c cookies.txt -X POST http://localhost:8080/login \
  -d "username=admin&password=admin123&workspace=default"
```

**Response:**
HTTP 302 Redirect to `/` with session cookie set

#### Test WireMock Endpoint

**Request:**
```bash
curl http://localhost:9999/hello
```

**Response:**
```json
{
  "message": "world",
  "timestamp": "2024-01-01T12:00:00Z",
  "source": "API Simulator"
}
```

**Error Codes:** 200 (OK), 201 (Created), 204 (No Content), 400 (Bad Request), 401 (Unauthorized), 403 (Forbidden), 404 (Not Found), 500 (Server Error)

**Error Format:** `{"timestamp", "status", "error", "message", "path"}`

## Testing

**Unit:** JUnit 5, Mockito, AssertJ. **Integration:** Spring Boot Test, embedded MongoDB, `@SpringBootTest`, `@DataMongoTest`.
**Coverage:** Service logic, repository queries, pattern matching, delay/chaos. **Jacoco:** Add plugin to pom.xml.

**Commands:**
```bash
mvn test                           # All tests
mvn test -Dtest=MappingServiceTest # Specific class
mvn test jacoco:report             # Coverage (target/site/jacoco/index.html)
mvn clean package -DskipTests      # Skip tests
```

**Location:** `backend/src/test/java/com/simulator/` - Naming: `*Test.java`, `testMethodName_scenario_expectedResult()`

**Mocking:** Use `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` for service tests. Use `@DataMongoTest` for repository tests with embedded MongoDB.

## Building & Deployment

**Build:** `mvn clean package` → Output: `target/api-simulator-1.0.0.jar` (executable JAR with Tomcat)
**Profiles:** Dev: `mvn clean package`, Prod: `mvn clean package -DskipTests && java -Dspring.profiles.active=production -jar target/api-simulator-1.0.0.jar`

**Docker:** Dockerfile uses `openjdk:17-jdk-slim`, copies JAR, exposes 8080, health check via Actuator
**docker-compose.yml:** MongoDB (persistent volume) + backend (depends on MongoDB health), bridge network
**Build/Run:** `./build.sh` OR `cd backend && mvn clean package -DskipTests && cd .. && docker-compose up --build`

**CI/CD:** GitHub Actions - checkout → setup-java:17 → mvn package → mvn test → docker build

**Deploy:**
- Docker Compose: `docker-compose up -d`, `docker-compose logs -f`, `docker-compose down`
- Kubernetes: Deployment with replicas, MongoDB URI via env
- Cloud: AWS ECS/Fargate, GCP Cloud Run, Azure Container Instances

## Troubleshooting

**1. MongoDB Connection Failed:** Check: `docker ps | grep mongo`, `telnet localhost 27017`, `echo $MONGO_URI`, `mongosh mongodb://localhost:27017/simulator`

**2. WireMock Mappings Not Loading:** `docker-compose logs backend | grep ERROR`, UI "Refresh WireMock", `db.mappings.find({enabled: true, deleted: {$ne: true}})`, `curl -X POST http://localhost:8080/admin/mappings/refresh`

**3. Port Conflicts:** `lsof -i :8080` / `netstat -ano | findstr :8080`, kill process or change docker-compose.yml port mapping

**4. Auth/Session Issues:** Check `AUTH_ENABLED`, clear cookies, verify user in DB: `db.userProfiles.findOne({userId: "admin"})`, check session timeout

**5. JSONPath Not Matching:** Test at https://jsonpath.com, verify valid JSON, check case sensitivity, enable DEBUG logging, review http://localhost:9999/__admin/requests

**Debug:** Set `logging.level.com.simulator=DEBUG` in application.yml OR `export LOGGING_LEVEL_COM_SIMULATOR=DEBUG`. Java debug: `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar target/api-simulator-1.0.0.jar` (connect IDE to port 5005)

**Logs:** Docker: `docker-compose logs [-f] backend`, Local: stdout/stderr or configure `logging.file.name: logs/api-simulator.log`

**Performance:** Ensure indexes, tune connection pool (`?maxPoolSize=50`), limit active mappings, increase JVM heap (`-Xmx2g`), use pagination, enable Thymeleaf cache in prod

## Dependencies & Updates

**Major Deps:** Spring Boot 3.2.0, WireMock 3.3.1, Spring Data MongoDB 3.x, Jackson 2.x, Thymeleaf 3.x, JSONPath, Commons Lang3 3.x, Embedded MongoDB 4.11.0

**Update:** `mvn versions:display-dependency-updates`, `mvn versions:display-plugin-updates`. Update pom.xml properties or parent version. Test thoroughly, review release notes, update one at a time.

**Compatibility:** WireMock 3.x needs Java 17+, Spring Boot 3.x needs Jakarta EE 9+ (javax→jakarta), Spring Data MongoDB 3.x needs MongoDB 4.0+, Embedded MongoDB 4.11.0 compatible with Spring Boot 3.2.0

## Security Considerations

**Current Auth:** Session-based, username/password in MongoDB, simple password hashing (NOT production-grade), session cookies
**Auth Recommendations:** BCrypt/Argon2 hashing, CSRF protection, account lockout, MFA

**Authorization:** RBAC (Admin/User), namespace-based access, SessionManager validation
**Access Matrix:** Admin (full), User (own namespace CRUD), Anonymous (WireMock API/test endpoints only)

**Encryption Current:** None at rest, no HTTPS, simple password hashing
**Encryption Recommendations:** Enable HTTPS (SSL keystore), MongoDB encryption at rest, TLS for MongoDB connection

**Best Practices Followed:** Soft delete, input validation, parameterized queries (NoSQL injection prevention), no secret hardcoding, health endpoint security

**Improvements Needed:** BCrypt/Argon2, Spring Security, CSRF, rate limiting, audit logging, secrets encryption (Vault/AWS), HTTPS/TLS, security headers (HSTS/CSP/X-Frame-Options)

## Performance Considerations

**Caching Current:** None (app-level), Thymeleaf cache disabled (dev), no query caching
**Caching Recommendations:** Spring Cache Abstraction (`@Cacheable`), Redis for distributed cache, HTTP `Cache-Control` headers

**Query Optimization Current:** Indexes on `namespace`/`enabled`/`priority`, compound index `{namespace:1, enabled:1, priority:-1}`, pagination
**Query Recommendations:** Projection queries (fetch only needed fields), aggregation pipelines, tune connection pooling

**Assets Current:** TailwindCSS/htmx from CDN, no image optimization
**Assets Recommendations:** Bundle/minify CSS (PostCSS/PurgeCSS), self-host libraries, enable Gzip/Brotli

**Scalability Current:** Stateful (in-memory sessions), single WireMock per app, MongoDB connection limit
**Horizontal Scaling:** Externalize sessions (Spring Session + Redis), stateless WireMock, load balancer, DB sharding by namespace
**Vertical Scaling:** Increase JVM heap (`-Xmx4g`), increase MongoDB pool (`?maxPoolSize=100`), optimize WireMock threads

---

**Document Version:** 1.0
**Last Updated:** 2024-10-01
**Maintainer:** API Simulator Development Team
