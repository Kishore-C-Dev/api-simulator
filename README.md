# API Simulator

A WireMock-backed API simulator with a modern web interface built using Spring Boot 3, htmx, TailwindCSS, and MongoDB. Create, manage, and test mock REST APIs with JSON-only responses, configurable delays, and chaos engineering features.

## 🚀 Features

- **WireMock Integration**: Embedded WireMock server for reliable API simulation
- **MongoDB Persistence**: All mappings and configurations stored in MongoDB
- **Modern Web UI**: htmx + TailwindCSS for responsive, interactive interface
- **JSON-Only Responses**: Focused on JSON API simulation (no binary/Base64)
- **Advanced Matching**: Path patterns, regex, headers, query params, JSONPath
- **Delay Simulation**: Fixed or variable delays with jitter
- **Chaos Engineering**: Configurable error rates with alternate error responses
- **Handlebars Templating**: Dynamic response generation with request data
- **Import/Export**: JSON-based configuration backup and sharing
- **Test Interface**: Built-in test panel for API validation
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
   - **Web UI**: http://localhost:8080
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
   - **Web UI**: http://localhost:8080
   - **WireMock API**: http://localhost:9999

## 🎯 Default Seed Mappings

The application comes with three pre-configured endpoints for immediate testing:

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
```

## 🖥️ Web Interface

### Dashboard
- View all API mappings in a paginated table
- Search mappings by name
- Quick enable/disable toggles
- Priority-based sorting

### Create/Edit Mapping Form
- **Request Configuration**: Method, path, headers, query params, body patterns
- **Response Configuration**: Status, headers, JSON body with templating
- **Delay Settings**: Fixed or variable delays
- **Chaos Engineering**: Error rate percentage and alternate error responses

### Test Panel
- Built-in JSON echo endpoint for testing
- Quick test buttons for common scenarios
- Real-time response display
- WireMock endpoint testing guidance

### Import/Export
- JSON-based configuration backup
- Sample mapping downloads
- Bulk import functionality

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

### Test API

| Endpoint | Method | Description |
|----------|---------|-------------|
| `/test/echo` | POST | Echo JSON request with metadata |

### WireMock API

All configured mappings are available on port `9999`. For example:
- `http://localhost:9999/hello`
- `http://localhost:9999/orders`
- `http://localhost:9999/your-custom-endpoint`

## 📝 Mapping Configuration

### Request Matching

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
        "matcher": "jsonPath",
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
| `ACTIVE_DATASET` | `default` | Active dataset name |
| `SERVER_PORT` | `8080` | Spring Boot server port |
| `AUTH_ENABLED` | `false` | Enable authentication (future) |

### Application Properties

```yaml
simulator:
  active-dataset: default
  wiremock:
    port: 9999
    admin-port: 9998
  auth:
    enabled: false
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
   ```

3. **Port Conflicts**
   ```
   Solution: Modify ports in docker-compose.yml
   Defaults: 8080 (app), 9999 (wiremock), 27017 (mongo)
   ```

4. **Template Rendering Issues**
   ```
   Solution: Verify Handlebars syntax in response bodies
   Check: Enable templating checkbox for dynamic responses
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

- [ ] Dataset switching and management UI
- [ ] User authentication and authorization
- [ ] API usage analytics and metrics
- [ ] OpenAPI/Swagger spec import
- [ ] GraphQL endpoint simulation
- [ ] Webhook simulation capabilities
- [ ] Request/response transformation pipelines
- [ ] A/B testing support with multiple response variants

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