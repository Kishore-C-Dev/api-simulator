# API Simulator

A WireMock-backed API simulator with a modern web interface built using Spring Boot 3, htmx, TailwindCSS, and MongoDB. Create, manage, and test mock REST APIs with JSON-only responses, configurable delays, chaos engineering features, and comprehensive user management with workspace isolation.

## 🚀 Features

- **WireMock Integration**: Embedded WireMock server for reliable API simulation
- **MongoDB Persistence**: All mappings and configurations stored in MongoDB
- **Modern Web UI**: htmx + TailwindCSS for responsive, interactive interface
- **JSON-Only Responses**: Focused on JSON API simulation (no binary/Base64)
- **Unified Request Builder**: Single interface for all request matching conditions
- **Advanced Pattern Matching**: JSONPath, XPath, regex, headers, query params, body patterns
- **Priority-Based Routing**: Multiple endpoints with configurable priority order
- **Delay Simulation**: Fixed or variable delays with jitter
- **Chaos Engineering**: Configurable error rates with alternate error responses
- **Handlebars Templating**: Dynamic response generation with request data
- **Import/Export**: JSON-based configuration backup and sharing
- **Test Interface**: Built-in test panel for API validation
- **User Management**: Multi-user support with role-based access control (Admin/User roles)
- **Workspace Isolation**: Complete isolation between different teams/projects
- **Session-Based Authentication**: Secure user sessions with workspace switching
- **Admin Dashboard**: Dedicated admin interface for user and workspace management
- **Docker Support**: Complete containerization with Docker Compose

## 📋 Quick Start

### Option 1: Docker Compose (Recommended)

1. **Clone and navigate to the project**:
   ```bash
   git clone <repository-url>
   cd api-simulator
   ```

2. **Build and start services**:
   ```bash
   # Build the Spring Boot application
   cd backend && mvn clean package -DskipTests && cd ..
   
   # Start all services
   docker-compose up --build
   ```

3. **Access the application**:
   - **Web UI**: http://localhost:8080 (redirects to login)
   - **Admin Panel**: http://localhost:8080/admin/ui (Admin users only)
   - **Login Credentials**:
     - Admin: `admin` / `admin123` (access: default, demo workspaces)
     - Demo User: `demo` / `demo123` (access: demo workspace only)
   - **WireMock API**: http://localhost:9999
   - **Admin API**: http://localhost:8080/admin

### Option 2: Local Development

1. **Prerequisites**:
   - Java 17+
   - Maven 3.6+
   - MongoDB 7.0+ (running on localhost:27017)

2. **Run the application**:
   ```bash
   cd backend
   mvn spring-boot:run
   ```

3. **Access the application**:
   - **Web UI**: http://localhost:8080 (redirects to login)
   - **Login required**: Use admin/admin123 or demo/demo123
   - **WireMock API**: http://localhost:9999

## 🎯 Default Seed Mappings

The application comes with five pre-configured endpoints demonstrating various features:

### 1. Hello World Endpoint
- **Method**: GET
- **Path**: `/hello`
- **Response**: `{"message": "world", "timestamp": "2024-01-01T12:00:00Z", "source": "API Simulator"}`
- **Delay**: Fixed 200ms

### 2. Order Creation Endpoint  
- **Method**: POST
- **Path**: `/orders`
- **Response**: Echoes request body with generated ID
- **Delay**: Variable 100-800ms
- **Example Response**:
  ```json
  {
    "id": "uuid-generated",
    "status": "created",
    "timestamp": "2024-01-01T12:00:00Z",
    "receivedData": {...your request body...}
  }
  ```

### 3. Flaky Endpoint (Chaos Engineering)
- **Method**: GET  
- **Path**: `/flaky`
- **Success Rate**: 85% (returns 200)
- **Error Rate**: 15% (returns 500)
- **Delay**: Fixed 300ms

### 4. Memo Endpoint - Account Validation (Priority: 1)
- **Method**: POST
- **Path**: `/api/memo`
- **Condition**: JSONPath validation for `account_number = "1234567890"`
- **Headers**: Requires `Content-Type: application/json`
- **Response**: Success message with account number
- **Status**: 200

### 5. Memo Endpoint - Missing Routing Number (Priority: 2)  
- **Method**: POST
- **Path**: `/api/memo`
- **Condition**: Regex pattern `^(?!.*routing_number).*$` (requests without routing_number)
- **Response**: Error message for missing routing number
- **Status**: 400

### 6. Memo Endpoint - Default Error (Priority: 5)
- **Method**: POST
- **Path**: `/api/memo`  
- **Condition**: Catch-all for other invalid requests
- **Response**: Generic error for invalid requests
- **Status**: 400

**Test these endpoints**:
```bash
# Test hello endpoint
curl http://localhost:9999/hello

# Test order creation
curl -X POST http://localhost:9999/orders \
  -H "Content-Type: application/json" \
  -d '{"item": "laptop", "quantity": 1}'

# Test flaky endpoint (run multiple times)
curl http://localhost:9999/flaky

# Test memo with valid account number
curl -X POST http://localhost:9999/api/memo \
  -H "Content-Type: application/json" \
  -d '{"account_number": "1234567890", "amount": 100}'

# Test memo missing routing number (triggers 400 error)
curl -X POST http://localhost:9999/api/memo \
  -H "Content-Type: application/json" \
  -d '{"account_number": "1234567890", "amount": 100}'

# Test memo with invalid data (triggers default error)
curl -X POST http://localhost:9999/api/memo \
  -H "Content-Type: application/json" \
  -d '{"invalid": "data"}'
```

## 🖥️ Web Interface

### Dashboard (http://localhost:8080/)
1. **Login**: Use admin/admin123 or demo/demo123
2. **Workspace Selection**: Switch between available workspaces in top nav
3. **Mapping Management**:
   - View all API mappings in a paginated table (workspace-isolated)
   - Search mappings by name
   - Quick enable/disable toggles
   - Priority-based sorting
   - Create/edit/delete mappings with workspace isolation

### Create/Edit Mapping Form
- **Unified Request Builder**: Single interface for all request matching conditions
  - Body Patterns (JSONPath, XPath, Regex, Contains, Exact)
  - Header Patterns (Exact, Regex, Contains, Exists)
  - Query Parameters (Exact, Regex, Contains, Exists)
  - Path Patterns (Exact, Wildcard, Regex)
- **Priority-Based Routing**: Configure endpoint priority (1-10, lower = higher priority)
- **Response Configuration**: Status, headers, JSON body with Handlebars templating
- **Delay Settings**: Fixed or variable delays with chaos engineering
- **Conditional Responses**: Request ID-based response variants

### Test Panel
- Built-in JSON echo endpoint for testing
- Quick test buttons for common scenarios
- Real-time response display
- WireMock endpoint testing guidance

### Admin Panel (http://localhost:8080/admin/ui) - Admin Users Only
1. **User Management**:
   - Create/edit/delete user accounts
   - Assign roles (Admin/User)
   - Manage workspace access permissions
2. **Workspace Management**:
   - Create/edit/delete workspaces
   - Assign users to workspaces
   - Isolate configurations between teams/projects

### Import/Export
- JSON-based configuration backup
- Sample mapping downloads
- Bulk import functionality (workspace-isolated)

## 🛠️ API Reference

### Admin REST API

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/admin/mappings` | GET | List all mappings (paginated) |
| `/admin/mappings/{id}` | GET | Get mapping by ID |
| `/admin/mappings` | POST | Create new mapping |
| `/admin/mappings/{id}` | PUT | Update existing mapping |
| `/admin/mappings/{id}` | DELETE | Delete mapping |
| `/admin/mappings/refresh` | POST | Reload WireMock stubs |
| `/admin/import` | POST | Import mappings from JSON file |
| `/admin/export` | GET | Export mappings as JSON |

### Authentication API

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/login` | POST | User login (returns session cookie) |
| `/logout` | POST | User logout |
| `/auth/current` | GET | Get current user session info |
| `/auth/switch-workspace` | POST | Switch active workspace |

### Admin User Management API

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/admin/users` | GET | List all users (Admin only) |
| `/admin/users` | POST | Create new user (Admin only) |
| `/admin/users/{id}` | PUT | Update user (Admin only) |
| `/admin/users/{id}` | DELETE | Delete user (Admin only) |
| `/admin/workspaces` | GET | List all workspaces |
| `/admin/workspaces` | POST | Create new workspace (Admin only) |
| `/admin/workspaces/{id}` | PUT | Update workspace (Admin only) |
| `/admin/workspaces/{id}` | DELETE | Delete workspace (Admin only) |

### Test API

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/test/echo` | POST | Echo JSON request with metadata |

### WireMock API

All configured mappings are available on port `9999`. For example:
- `http://localhost:9999/hello`
- `http://localhost:9999/orders`
- `http://localhost:9999/your-custom-endpoint`

### Authentication & Authorization Examples

```bash
# Login via API (returns session cookie)
curl -c cookies.txt -d "username=admin&password=admin123&workspace=default" \
  -X POST http://localhost:8080/login

# Use authenticated session for API calls
curl -b cookies.txt http://localhost:8080/admin/mappings

# Switch workspace in session
curl -b cookies.txt -d "workspace=demo" \
  -X POST http://localhost:8080/auth/switch-workspace

# Check current user session
curl -b cookies.txt http://localhost:8080/auth/current

# Admin-only operations (create user)
curl -b cookies.txt -X POST http://localhost:8080/admin/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "password": "password123",
    "role": "USER",
    "workspaces": ["demo"]
  }'

# Create new workspace (admin only)
curl -b cookies.txt -X POST http://localhost:8080/admin/workspaces \
  -H "Content-Type: application/json" \
  -d '{
    "name": "testteam",
    "displayName": "Test Team Workspace"
  }'

# Logout
curl -b cookies.txt -X POST http://localhost:8080/logout
```

## 📝 Mapping Configuration

### Unified Request Conditions

The new unified request builder consolidates all request matching into a single, intuitive interface:

**Body Patterns:**
- **JSONPath**: `$.account_number` equals `"1234567890"`
- **Regex**: `^(?!.*routing_number).*$` (negative matching for missing fields)
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
Multiple endpoints can share the same path with different conditions:
```
Priority 1: /api/memo + account_number=1234567890 → Success (200)
Priority 2: /api/memo + no routing_number → Error (400) 
Priority 5: /api/memo + any other request → Default Error (400)
```

### Legacy JSON Configuration

```json
{
  "request": {
    "method": "POST",
    "path": "/api/customers/{id}",
    "headers": {
      "Content-Type": "application/json",
      "X-Tenant": "demo"
    },
    "queryParams": {
      "active": "true"
    },
    "bodyPatterns": [
      {
        "matchType": "JSONPATH",
        "expr": "$.type",
        "expected": "premium"
      }
    ]
  }
}
```

### Response Configuration

```json
{
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "body": "{\"id\": \"{{request.pathSegments.[2]}}\", \"name\": \"Customer Name\", \"timestamp\": \"{{now}}\"}",
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
| `MONGO_URI` | `mongodb://localhost:27017/simulator` | MongoDB connection string |
| `ACTIVE_DATASET` | `default` | Active dataset name (deprecated - use workspaces) |
| `SERVER_PORT` | `8080` | Spring Boot server port |
| `AUTH_ENABLED` | `true` | Enable authentication and user management |

### Application Properties

```yaml
simulator:
  active-dataset: default  # Deprecated - use workspaces instead
  wiremock:
    port: 9999
    admin-port: 9998
  auth:
    enabled: true
    session-timeout: 3600  # Session timeout in seconds
```

## 🧪 Testing

### Run Unit Tests

```bash
cd backend
mvn test
```

### Test Coverage

The project includes comprehensive tests for:
- Service layer logic (DelayService, MappingService)
- Repository queries and operations
- REST API endpoints
- JSON processing and validation

### Manual Testing

1. **Use the Test Panel**: Built-in interface for JSON echo testing
2. **curl Commands**: Direct API testing via command line
3. **Postman/Insomnia**: Import OpenAPI specs for structured testing

## 🚀 Deployment

### Docker Production Deployment

1. **Configure environment**:
   ```bash
   cp .env.example .env
   # Edit .env with your production settings
   ```

2. **Deploy with Docker Compose**:
   ```bash
   docker-compose -f docker-compose.yml up -d
   ```

### Kubernetes Deployment

Create ConfigMaps and Deployments for:
- MongoDB (persistent storage)
- API Simulator application
- Ingress for external access

### Health Checks

- **Application Health**: `http://localhost:8080/actuator/health`
- **MongoDB Health**: Included in Docker Compose healthchecks
- **WireMock Health**: Verify via admin interface

## 🔍 Troubleshooting

### Common Issues

1. **MongoDB Connection Failed**
   ```
   Solution: Ensure MongoDB is running and accessible
   Check: MONGO_URI environment variable
   ```

2. **WireMock Mappings Not Loading**
   ```
   Solution: Check application logs for mapping errors
   Action: Use "Refresh WireMock" button in UI
   API: POST /admin/mappings/refresh
   ```

3. **WireMock Admin Shows No Objects**
   ```
   Solution: Mappings need to be manually loaded on startup
   Action: Use "Refresh WireMock" button or restart application
   Check: http://localhost:9999/__admin/mappings
   ```

4. **Port Conflicts**
   ```
   Solution: Modify ports in docker-compose.yml or application.yml
   Defaults: 8080 (app), 9999 (wiremock), 27017 (mongo)
   ```

5. **Unified Conditions Not Loading**
   ```
   Solution: Check browser console for JavaScript errors
   Action: Reload page, verify conditions display in Request Conditions section
   ```

6. **Priority-Based Routing Issues**
   ```
   Solution: Lower priority number = higher precedence
   Example: Priority 1 matches before Priority 5
   Check: WireMock processes by priority order
   ```

7. **Regex Patterns Not Working**
   ```
   Solution: Test regex patterns in unified interface text area
   Example: ^(?!.*routing_number).*$ for negative matching
   Tool: Use online regex testers for validation
   ```

8. **Template Rendering Issues**
   ```
   Solution: Verify Handlebars syntax in response bodies
   Check: Enable templating checkbox for dynamic responses
   ```

9. **Authentication Issues**
   ```
   Solution: Check login credentials and session
   Action: Use admin/admin123 or demo/demo123
   Check: Ensure AUTH_ENABLED=true in configuration
   ```

10. **Workspace Access Issues**
    ```
    Solution: Check user's workspace permissions
    Action: Admin can assign users to workspaces
    API: GET /auth/current to check current session
    ```

11. **Session Timeout**
    ```
    Solution: Re-login if session expires
    Check: Default session timeout is 3600 seconds (1 hour)
    Config: Adjust session-timeout in application.yml
    ```

### Logging

- **Application Logs**: Check Docker logs or console output
- **MongoDB Logs**: Available in Docker container logs
- **WireMock Logs**: Integrated with application logging

### Performance Tuning

- **MongoDB Indexing**: Automatic indexes on dataset and enabled fields
- **Memory Settings**: Configure JVM heap size for larger datasets
- **Connection Pooling**: MongoDB connection pool sizing

## 🔮 Future Enhancements

- [ ] API usage analytics and metrics dashboard
- [ ] OpenAPI/Swagger spec import
- [ ] GraphQL endpoint simulation
- [ ] Webhook simulation capabilities
- [ ] Request/response transformation pipelines
- [ ] A/B testing support with multiple response variants
- [ ] Condition validation with real-time testing
- [ ] Template editor with syntax highlighting
- [ ] Bulk condition operations (copy, move, delete)
- [ ] Advanced regex builder with pattern suggestions
- [ ] Workspace-level statistics and reporting
- [ ] User activity audit logs
- [ ] Backup and restore functionality
- [ ] LDAP/SSO integration for enterprise users

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📞 Support

- **Issues**: Report bugs and request features via GitHub Issues
- **Documentation**: This README and inline code documentation
- **Community**: Discussions and Q&A via GitHub Discussions

---

**Built with ❤️ using Spring Boot 3, WireMock, htmx, TailwindCSS, and MongoDB**