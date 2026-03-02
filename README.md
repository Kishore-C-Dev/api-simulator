# API Simulator

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue)
![License](https://img.shields.io/badge/license-MIT-blue)

A WireMock-backed API simulator with a modern web interface built using Spring Boot 3, htmx, TailwindCSS, and PostgreSQL. Create, manage, and test mock REST APIs with JSON-only responses, configurable delays, chaos engineering features, and comprehensive user management with workspace isolation.

The system is split into two independent services:
- **Admin Console** (port 8080) — web UI for creating and managing mock mappings, users, and workspaces.
- **Mock Server** (port 9999) — headless WireMock instance that serves simulated API responses for a given namespace.

**Perfect for:** Development teams needing to mock external APIs, test error scenarios, simulate network latency, and maintain isolated workspaces for different projects.

## Table of Contents

- [Features](#-features)
- [Architecture](#-architecture)
- [Quick Start](#-quick-start)
- [Demo Endpoints](#-default-seed-mappings)
- [Web Interface](#-web-interface)
- [API Reference](#-api-reference)
- [Configuration](#-configuration-options)
- [Testing](#-testing)
- [Deployment](#-deployment)
- [Troubleshooting](#-troubleshooting)
- [Contributing](#-contributing)
- [License](#-license)

## 🚀 Features

### Core Capabilities
- **WireMock Integration**: Embedded WireMock server for reliable API simulation
- **PostgreSQL Persistence**: All mappings and configurations stored in PostgreSQL with JSONB columns for flexible schema
- **Modern Web UI**: htmx + TailwindCSS for responsive, interactive interface
- **JSON-Only Responses**: Focused on JSON API simulation

### Advanced Matching & Routing
- **Unified Request Builder**: Single interface for all request matching conditions
- **Advanced Pattern Matching**: JSONPath, XPath, regex, headers, query params, body patterns
- **Priority-Based Routing**: Multiple endpoints with configurable priority order
- **GraphQL Support**: Full GraphQL endpoint simulation capabilities

### Testing & Resilience
- **Delay Simulation**: Fixed or variable delays with jitter
- **Chaos Engineering**: Configurable error rates with alternate error responses
- **Handlebars Templating**: Dynamic response generation with request data
- **Test Interface**: Built-in test panel for API validation

### Enterprise Features
- **User Management**: Multi-user support with role-based access control (Admin/User roles)
- **Workspace Isolation**: Complete isolation between different teams/projects
- **Session-Based Authentication**: Secure user sessions with workspace switching
- **Admin Dashboard**: Dedicated admin interface for user and workspace management
- **Import/Export**: JSON-based configuration backup and sharing
- **Docker Support**: Complete containerization with Docker Compose

## 🏗️ Architecture

```
┌──────────────────────────────────┐      ┌─────────────────────────────────┐
│   Admin Console  (port 8080)     │      │   Mock Server   (port 9999)     │
│                                  │      │                                 │
│  Web UI  ──►  MappingService     │      │  WireMock ◄── MockServerInit   │
│               UserService         │─ POST /refresh ──►                   │
│               NamespaceService   │      │  (reloads stubs from DB)        │
└──────────────┬───────────────────┘      └───────────────┬─────────────────┘
               │                                          │
               └──────────────┬───────────────────────────┘
                              │
                  ┌───────────▼───────────┐
                  │   PostgreSQL  (5432)  │
                  │  mappings, users,     │
                  │  namespaces           │
                  └───────────────────────┘
```

**Communication flow:** Admin Console saves a mapping → notifies each registered Mock Server via `POST /refresh` → Mock Server reloads its stubs from PostgreSQL → simulated API is immediately live on port 9999.

Multiple Mock Server instances can be registered (one per namespace) via the `MOCK_SERVER_URLS` environment variable.

## 📋 Quick Start

### Option 1: Docker Compose (Recommended)

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd api-simulator
   ```

2. **Build and start all services**:
   ```bash
   docker-compose up --build -d
   ```

   This starts three containers:
   - `api-simulator-postgres` — PostgreSQL 16 database
   - `api-simulator-admin-console` — Admin web UI
   - `api-simulator-mock-server-default` — WireMock for the `default` namespace

3. **Access the application**:
   - **Web UI**: http://localhost:8080
   - **Login Credentials**:
     - Admin: `admin` / `admin123` (access: default, demo workspaces)
     - Demo User: `demo` / `demo123` (access: demo workspace only)
   - **Simulated APIs**: http://localhost:9999
   - **Admin API**: http://localhost:8080/admin

### Option 2: Local Development

1. **Prerequisites**:
   - Java 17+
   - Maven 3.6+
   - PostgreSQL 16 (running on localhost:5432 with database `appdb`, user `appuser`, password `apppass`)

2. **Start admin-console**:
   ```bash
   cd admin-console
   mvn spring-boot:run
   ```

3. **Start mock-server** (separate terminal):
   ```bash
   cd mock-server
   mvn spring-boot:run
   ```

4. **Access the application**:
   - **Web UI**: http://localhost:8080
   - **Simulated APIs**: http://localhost:9999
   - **Mock Server management**: http://localhost:8090

## 🎯 Default Seed Mappings

The application starts with five pre-configured endpoints demonstrating various features:

### 1. Hello World Endpoint
- **Method**: GET
- **Path**: `/hello`
- **Response**: `{"message": "world", "timestamp": "...", "source": "API Simulator"}`
- **Delay**: Fixed 200ms

### 2. Order Creation Endpoint
- **Method**: POST
- **Path**: `/orders`
- **Response**: Echoes request body with generated ID
- **Delay**: Variable 100–800ms
- **Example Response**:
  ```json
  {
    "id": "uuid-generated",
    "status": "created",
    "timestamp": "...",
    "receivedData": { "...your request body..." }
  }
  ```

### 3. Flaky Endpoint (Chaos Engineering)
- **Method**: GET
- **Path**: `/flaky`
- **Success Rate**: 85% (returns 200)
- **Error Rate**: 15% (returns 500)
- **Delay**: Fixed 300ms

### 4. Memo Endpoint — Account Validation (Priority: 1)
- **Method**: POST / **Path**: `/api/memo`
- **Condition**: JSONPath `$.account_number` equals `1234567890`
- **Response**: 200 Success

### 5. Memo Endpoint — Missing Routing Number (Priority: 2)
- **Method**: POST / **Path**: `/api/memo`
- **Condition**: Regex `^(?!.*routing_number).*$`
- **Response**: 400 error

### 6. Memo Endpoint — Default Error (Priority: 5)
- **Method**: POST / **Path**: `/api/memo`
- **Condition**: Catch-all
- **Response**: 400 error

**Test these endpoints**:
```bash
# Hello World
curl http://localhost:9999/hello

# Order creation
curl -X POST http://localhost:9999/orders \
  -H "Content-Type: application/json" \
  -d '{"item": "laptop", "quantity": 1}'

# Flaky endpoint (run multiple times to see errors)
curl http://localhost:9999/flaky

# Memo — valid account
curl -X POST http://localhost:9999/api/memo \
  -H "Content-Type: application/json" \
  -d '{"account_number": "1234567890", "routing_number": "021000021", "amount": 100}'

# Memo — missing routing number (400)
curl -X POST http://localhost:9999/api/memo \
  -H "Content-Type: application/json" \
  -d '{"account_number": "1234567890", "amount": 100}'

# Memo — invalid data (400)
curl -X POST http://localhost:9999/api/memo \
  -H "Content-Type: application/json" \
  -d '{"invalid": "data"}'
```

## 🖥️ Web Interface

### Dashboard (http://localhost:8080/)
1. **Login**: Use admin/admin123 or demo/demo123
2. **Workspace Selection**: Switch between available workspaces in the top nav
3. **Mapping Management**:
   - View all API mappings in a paginated table (workspace-isolated)
   - Search mappings by name
   - Quick enable/disable toggles
   - Priority-based sorting
   - Create/edit/delete mappings

### Create/Edit Mapping Form
- **Unified Request Builder**: Single interface for all request matching conditions
  - Body Patterns (JSONPath, XPath, Regex, Contains, Exact)
  - Header Patterns (Exact, Regex, Contains, Exists)
  - Query Parameters (Exact, Regex, Contains, Exists)
  - Path Patterns (Exact, Wildcard, Regex)
- **Priority-Based Routing**: Configure endpoint priority (1–10, lower = higher priority)
- **Response Configuration**: Status, headers, JSON body with Handlebars templating
- **Delay Settings**: Fixed or variable delays with chaos engineering
- **Conditional Responses**: Request ID-based response variants

### Test Panel
- Built-in JSON echo endpoint for testing
- Quick test buttons for common scenarios
- Real-time response display

### Admin Panel (http://localhost:8080/admin/ui) — Admin Users Only
1. **User Management**: Create/edit/delete accounts, assign roles, manage workspace access
2. **Workspace Management**: Create/edit/delete workspaces, assign users

### Import/Export
- JSON-based configuration backup
- Sample mapping downloads
- Bulk import (workspace-isolated)

## 🛠️ API Reference

### Admin REST API (port 8080)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/admin/mappings` | GET | List all mappings (paginated) |
| `/admin/mappings/{id}` | GET | Get mapping by ID |
| `/admin/mappings` | POST | Create new mapping |
| `/admin/mappings/{id}` | PUT | Update existing mapping |
| `/admin/mappings/{id}` | DELETE | Delete mapping |
| `/admin/mappings/refresh` | POST | Push mappings to all mock servers |
| `/admin/import` | POST | Import mappings from JSON file |
| `/admin/export` | GET | Export mappings as JSON |

### Authentication API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/login` | POST | User login (returns session cookie) |
| `/logout` | POST | User logout |
| `/auth/current` | GET | Get current user session info |
| `/auth/switch-workspace` | POST | Switch active workspace |

### Admin User & Workspace Management API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/admin/users` | GET | List all users (Admin only) |
| `/admin/users` | POST | Create new user (Admin only) |
| `/admin/users/{id}` | PUT | Update user (Admin only) |
| `/admin/users/{id}` | DELETE | Delete user (Admin only) |
| `/admin/workspaces` | GET | List all workspaces |
| `/admin/workspaces` | POST | Create new workspace (Admin only) |
| `/admin/workspaces/{id}` | PUT | Update workspace (Admin only) |
| `/admin/workspaces/{id}` | DELETE | Delete workspace (Admin only) |

### Mock Server Management API (port 8090)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/refresh` | POST | Reload WireMock stubs from database |
| `/actuator/health` | GET | Mock server health check |

### Test API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/test/echo` | POST | Echo JSON request with metadata |

### Authentication Examples

```bash
# Login (returns session cookie)
curl -c cookies.txt -d "username=admin&password=admin123&workspace=default" \
  -X POST http://localhost:8080/login

# Use authenticated session
curl -b cookies.txt http://localhost:8080/admin/mappings

# Switch workspace
curl -b cookies.txt -d "workspace=demo" \
  -X POST http://localhost:8080/auth/switch-workspace

# Create a user (admin only)
curl -b cookies.txt -X POST http://localhost:8080/admin/users \
  -H "Content-Type: application/json" \
  -d '{"username": "newuser", "password": "password123", "role": "USER", "workspaces": ["demo"]}'

# Logout
curl -b cookies.txt -X POST http://localhost:8080/logout
```

## 🛠️ Technologies Used

- **Backend:** Java 17, Spring Boot 3.2.0, WireMock 3.3.1
- **Database:** PostgreSQL 16 (JPA/Hibernate, JSONB for complex fields)
- **Frontend:** htmx, TailwindCSS, Thymeleaf
- **Build:** Maven 3.6+
- **Containerization:** Docker, Docker Compose

## 📝 Mapping Configuration

### Request Matching

**Body Patterns:**
- **JSONPath**: `$.account_number` equals `"1234567890"`
- **Regex**: `^(?!.*routing_number).*$` (negative look-ahead)
- **Contains**: Text substring matching
- **Exact**: Exact string matching
- **XPath**: XML path expressions

**Header Patterns:**
- **Exact**: `Content-Type` equals `application/json`
- **Contains**: `User-Agent` contains `mobile`
- **Regex**: `Authorization` matches `Bearer .*`
- **Exists**: Header must be present

**Query Parameters:**
- **Exact**: `status` equals `active`
- **Contains**: `search` contains `product`
- **Regex**: `id` matches `\d+`
- **Exists**: Parameter must be present

**Priority-Based Routing:**
```
Priority 1: /api/memo + account_number=1234567890  →  200 Success
Priority 2: /api/memo + no routing_number          →  400 Error
Priority 5: /api/memo + any other request          →  400 Default Error
```

### Response Configuration

```json
{
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "body": "{\"id\": \"{{request.pathSegments.[2]}}\", \"timestamp\": \"{{now}}\"}",
    "templatingEnabled": true
  }
}
```

### Delay and Chaos Configuration

```json
{
  "delays": {
    "mode": "variable",
    "variableMinMs": 100,
    "variableMaxMs": 500,
    "errorRatePercent": 10,
    "errorResponse": {
      "status": 503,
      "body": "{\"error\": \"Service temporarily unavailable\"}"
    }
  }
}
```

## 🔧 Configuration Options

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/appdb` | PostgreSQL connection URL |
| `DATABASE_USERNAME` | `appuser` | PostgreSQL username |
| `DATABASE_PASSWORD` | `apppass` | PostgreSQL password |
| `SERVER_PORT` | `8080` (admin) / `8090` (mock) | HTTP server port |
| `AUTH_ENABLED` | `false` | Enable authentication |
| `MOCK_SERVER_URLS` | _(empty)_ | Comma-separated mock server URLs to notify on mapping changes |
| `SIMULATOR_NAMESPACE` | `default` | Namespace loaded by a mock-server instance |
| `WIREMOCK_PORT` | `9999` | WireMock listener port |

### Application Properties (admin-console)

```yaml
simulator:
  active-dataset: default
  auth:
    enabled: false
  mock-server:
    urls: http://mock-server-default:8090
```

## 🧪 Testing

### Run Unit Tests

```bash
# Admin Console
cd admin-console && mvn test

# Mock Server
cd mock-server && mvn test
```

### Test Coverage

- Service layer logic (MappingService, UserService)
- Repository queries (JPQL and native PostgreSQL)
- REST API endpoints
- JSON processing and pattern matching

### Manual Testing

1. **Test Panel**: Built-in interface at http://localhost:8080/test
2. **curl**: See examples in [Demo Endpoints](#-default-seed-mappings) and [API Reference](#-api-reference)
3. **WireMock Admin**: http://localhost:9999/__admin/mappings

## 🚀 Deployment

### Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Restart a service
docker-compose restart admin-console

# Stop everything
docker-compose down

# Stop and remove volumes (resets database)
docker-compose down -v
```

### Adding a Second Mock Server (new namespace)

Add an additional service to `docker-compose.yml`:

```yaml
  mock-server-teamalpha:
    build:
      context: ./mock-server
      dockerfile: Dockerfile
    ports:
      - "9998:9999"
      - "8091:8090"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/appdb
      - SPRING_DATASOURCE_USERNAME=${DATABASE_USERNAME:-appuser}
      - SPRING_DATASOURCE_PASSWORD=${DATABASE_PASSWORD:-apppass}
      - SIMULATOR_NAMESPACE=team-alpha
      - SERVER_PORT=8090
      - WIREMOCK_PORT=9999
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - simulator-network
```

Then register it in admin-console:
```
MOCK_SERVER_URLS=http://mock-server-default:8090,http://mock-server-teamalpha:8090
```

### Health Checks

- **Admin Console**: http://localhost:8080/actuator/health
- **Mock Server**: http://localhost:8090/actuator/health
- **PostgreSQL**: Docker Compose healthcheck via `pg_isready`

### Kubernetes

Deploy admin-console and mock-server as separate Deployments, backed by a shared PostgreSQL instance. Pass `SPRING_DATASOURCE_*` credentials via Secrets, and `MOCK_SERVER_URLS` via a ConfigMap.

## 🔍 Troubleshooting

### Common Issues

1. **PostgreSQL Connection Failed**
   ```
   Check: SPRING_DATASOURCE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
   Docker: docker ps | grep postgres
   Verify: pg_isready -h localhost -U appuser -d appdb
   ```

2. **WireMock Mappings Not Loading**
   ```
   Action: Click "Refresh WireMock" in the UI or POST /admin/mappings/refresh
   Check:  docker-compose logs mock-server-default | grep ERROR
   Verify: curl http://localhost:9999/__admin/mappings
   ```

3. **Mock Server Not Receiving Updates**
   ```
   Check: MOCK_SERVER_URLS is set correctly in admin-console environment
   Test:  curl -X POST http://localhost:8090/refresh
   Logs:  docker-compose logs admin-console | grep "Notifying mock server"
   ```

4. **Port Conflicts**
   ```
   Ports: 8080 (admin UI), 9999 (WireMock), 8090 (mock management), 5432 (PostgreSQL)
   Fix:   Modify host port mappings in docker-compose.yml
   Check: lsof -i :8080
   ```

5. **Priority-Based Routing Issues**
   ```
   Lower number = higher precedence (Priority 1 wins over Priority 5)
   Check: GET http://localhost:9999/__admin/mappings (shows WireMock's loaded stubs)
   ```

6. **Authentication Issues**
   ```
   Default credentials: admin/admin123 or demo/demo123
   Check: AUTH_ENABLED environment variable
   Action: Clear browser cookies and re-login
   ```

7. **Session Timeout**
   ```
   Default: 3600 seconds (1 hour)
   Action: Re-login
   ```

8. **Regex Patterns Not Matching**
   ```
   Test your regex at https://regex101.com
   Enable DEBUG logging: logging.level.com.simulator=DEBUG
   Check WireMock requests: http://localhost:9999/__admin/requests
   ```

9. **Template Rendering Issues**
   ```
   Verify Handlebars syntax in response body
   Ensure "Enable Templating" is checked on the mapping
   ```

10. **Workspace Access Denied**
    ```
    Check user's workspace membership via Admin Panel
    API: GET /auth/current to see current session and namespace
    ```

### Logging

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f admin-console
docker-compose logs -f mock-server-default

# Increase log level (application.yml)
logging:
  level:
    com.simulator: DEBUG
```

### Performance Tuning

- PostgreSQL indexes are created automatically by `db/schema.sql` on `namespace`, `enabled`, and `priority` columns
- Increase JVM heap: add `JAVA_OPTS=-Xmx2g` to Docker environment
- Tune PostgreSQL connections via `?maxPoolSize=50` in the JDBC URL

## 🤝 Contributing

1. **Fork the repository**
2. **Create a feature branch**
   ```bash
   git checkout -b feature/amazing-feature
   ```
3. **Make your changes**
   - Follow existing code style (4-space indent, 120-char line limit)
   - Add tests for new functionality
   - Update documentation as needed
4. **Commit and push**
   ```bash
   git commit -m 'Add amazing feature'
   git push origin feature/amazing-feature
   ```
5. **Open a Pull Request**

### Development Setup

See [CLAUDE.md](CLAUDE.md) for full architecture details, database schema, and development workflow.

## 📄 License

This project is licensed under the MIT License.

## 👥 Acknowledgments

Built with these technologies:
- [Spring Boot](https://spring.io/projects/spring-boot) — Application framework
- [WireMock](https://wiremock.org/) — API simulation engine
- [PostgreSQL](https://www.postgresql.org/) — Relational database
- [htmx](https://htmx.org/) — Dynamic HTML interactions
- [TailwindCSS](https://tailwindcss.com/) — Utility-first CSS
