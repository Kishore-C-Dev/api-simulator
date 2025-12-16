# CI/CD Integration with Existing MongoDB Mocks

**Scenario:** You've created API mocks using the Web UI, and they're stored in your MongoDB database.

**Goal:** Reuse these existing mocks in your CI/CD pipeline without re-importing them.

---

## Quick Start (3 Steps)

### **Step 1: Point to Your MongoDB**

```yaml
# .github/workflows/test.yml
services:
  api-simulator:
    image: api-simulator:latest
    ports:
      - "9999:9999"
      - "8080:8080"
    env:
      # Point to YOUR existing MongoDB with the mocks
      MONGO_URI: mongodb://your-production-mongo:27017/simulator
      AUTH_ENABLED: false
```

### **Step 2: Refresh WireMock (Load from MongoDB)**

```yaml
steps:
  - name: Wait and Refresh Mappings
    run: |
      # Wait for API Simulator to start
      timeout 60 sh -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'

      # Refresh WireMock to load mappings from MongoDB
      curl -X POST http://localhost:8080/admin/mappings/refresh

      # Verify mappings loaded
      echo "Loaded mappings:"
      curl -s http://localhost:8080/admin/mappings | jq -r '.[] | .name'
```

### **Step 3: Run Your Tests**

```yaml
  - name: Run Integration Tests
    run: npm test
    env:
      API_BASE_URL: http://localhost:9999
```

---

## Complete Examples

### **GitHub Actions - Using Production MongoDB**

```yaml
# .github/workflows/integration-tests.yml
name: Integration Tests (Existing Mocks)

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      api-simulator:
        image: api-simulator:latest
        ports:
          - 9999:9999
          - 8080:8080
        env:
          # Use your existing MongoDB (production, staging, or dedicated test DB)
          MONGO_URI: ${{ secrets.MONGO_URI }}  # e.g., mongodb://prod-mongo:27017/simulator
          AUTH_ENABLED: false
        options: >-
          --health-cmd "curl -f http://localhost:8080/actuator/health || exit 1"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 10

    steps:
      - uses: actions/checkout@v3

      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: 18

      - name: Install dependencies
        run: npm ci

      - name: Refresh WireMock Mappings
        run: |
          echo "Refreshing WireMock mappings from MongoDB..."
          curl -X POST http://localhost:8080/admin/mappings/refresh

          echo "Verifying mappings loaded:"
          curl -s http://localhost:8080/admin/mappings | jq -r '.[] | "\(.name) - \(.request.method) \(.request.path)"'

      - name: Run Tests
        run: npm test
        env:
          PAYMENT_API_URL: http://localhost:9999
          USER_API_URL: http://localhost:9999
          ORDER_API_URL: http://localhost:9999
```

### **Jenkins - Declarative Pipeline**

```groovy
// Jenkinsfile
pipeline {
    agent any

    environment {
        MONGO_URI = credentials('mongodb-connection-string')  // Stored in Jenkins
        API_SIMULATOR_IMAGE = 'api-simulator:latest'
    }

    stages {
        stage('Start API Simulator') {
            steps {
                script {
                    sh """
                        docker run -d --name api-simulator-${BUILD_NUMBER} \
                          -p 8080:8080 \
                          -p 9999:9999 \
                          -e MONGO_URI=${MONGO_URI} \
                          -e AUTH_ENABLED=false \
                          ${API_SIMULATOR_IMAGE}
                    """

                    // Wait for health check
                    sh """
                        timeout 60 sh -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'
                    """

                    // Refresh mappings from MongoDB
                    sh """
                        curl -X POST http://localhost:8080/admin/mappings/refresh
                        echo 'Mappings loaded from MongoDB'
                    """
                }
            }
        }

        stage('Run Tests') {
            steps {
                sh '''
                    export API_BASE_URL=http://localhost:9999
                    mvn test
                '''
            }
        }
    }

    post {
        always {
            sh "docker stop api-simulator-${BUILD_NUMBER} || true"
            sh "docker rm api-simulator-${BUILD_NUMBER} || true"
        }
    }
}
```

### **GitLab CI/CD**

```yaml
# .gitlab-ci.yml
stages:
  - test

variables:
  SIMULATOR_IMAGE: api-simulator:latest
  MONGO_URI: mongodb://your-mongo-host:27017/simulator

services:
  - name: $SIMULATOR_IMAGE
    alias: api-simulator
    variables:
      MONGO_URI: $MONGO_URI
      AUTH_ENABLED: "false"

before_script:
  - apk add --no-cache curl jq
  - timeout 60 sh -c 'until curl -f http://api-simulator:8080/actuator/health; do sleep 2; done'
  - curl -X POST http://api-simulator:8080/admin/mappings/refresh
  - echo "Mappings loaded from MongoDB"

integration-tests:
  stage: test
  script:
    - export API_BASE_URL=http://api-simulator:9999
    - npm run test:integration
```

### **Docker Compose for Local Testing**

```yaml
# docker-compose.ci.yml
version: '3.8'

services:
  api-simulator:
    image: api-simulator:latest
    ports:
      - "8080:8080"
      - "9999:9999"
    environment:
      # Connect to your existing MongoDB
      MONGO_URI: mongodb://your-mongo-host:27017/simulator
      AUTH_ENABLED: false
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 10

  test-runner:
    image: node:18
    depends_on:
      api-simulator:
        condition: service_healthy
    working_dir: /app
    volumes:
      - ./:/app
    environment:
      API_BASE_URL: http://api-simulator:9999
    command: |
      sh -c "
        echo 'Refreshing WireMock mappings...'
        curl -X POST http://api-simulator:8080/admin/mappings/refresh

        echo 'Running tests...'
        npm test
      "
```

**Run it:**
```bash
docker-compose -f docker-compose.ci.yml up --abort-on-container-exit
```

---

## MongoDB Connection Options

### **Option 1: Use Shared MongoDB (Recommended)**

Connect API Simulator to your existing MongoDB instance that already has the mocks:

```yaml
env:
  MONGO_URI: mongodb://production-mongo.example.com:27017/simulator
```

**Pros:**
- ✅ No need to import mocks
- ✅ Mocks stay in sync with what you created in UI
- ✅ Faster pipeline (no import step)

**Cons:**
- ⚠️ Tests share same mock database
- ⚠️ Concurrent builds may conflict

### **Option 2: MongoDB Replica/Read Replica**

Use a read replica of your production MongoDB:

```yaml
env:
  MONGO_URI: mongodb://mongo-replica.example.com:27017/simulator
```

**Pros:**
- ✅ Read-only access to production mocks
- ✅ No impact on production database

### **Option 3: Dedicated Test MongoDB**

Create a dedicated MongoDB for CI/CD and sync mocks to it:

```bash
# One-time setup: Export mocks from production
curl http://production:8080/admin/export > mocks-backup.json

# In CI/CD: Import to test MongoDB
curl -X POST http://localhost:8080/admin/import -F "file=@mocks-backup.json"
curl -X POST http://localhost:8080/admin/mappings/refresh
```

### **Option 4: MongoDB Atlas (Cloud)**

If using MongoDB Atlas:

```yaml
env:
  MONGO_URI: mongodb+srv://username:password@cluster.mongodb.net/simulator?retryWrites=true&w=majority
```

Store credentials securely:
- **GitHub Actions:** `${{ secrets.MONGO_URI }}`
- **Jenkins:** `credentials('mongo-uri')`
- **GitLab:** `$MONGO_URI` (CI/CD variables)

---

## Namespace Isolation Strategy

If you created mocks in different namespaces (workspaces), you can control which ones are loaded:

### **Load Specific Namespace**

```bash
# Set the active namespace/workspace
curl -X POST http://localhost:8080/admin/mappings/refresh?namespace=payment-team

# Or filter in your tests
export NAMESPACE=payment-team
npm test
```

### **Load All Namespaces**

```bash
# Default behavior - loads all enabled mappings from all namespaces
curl -X POST http://localhost:8080/admin/mappings/refresh
```

---

## Verification Steps

### **Check Mappings Loaded Successfully**

```bash
# List all loaded mappings
curl -s http://localhost:8080/admin/mappings | jq -r '.[] | "\(.name) - \(.enabled)"'

# Count loaded mappings
curl -s http://localhost:8080/admin/mappings | jq '. | length'

# Test a specific endpoint
curl http://localhost:9999/your-endpoint-path
```

### **Debug if Mappings Not Loading**

```bash
# Check MongoDB connection
curl http://localhost:8080/actuator/health | jq '.components.mongo'

# Check database has mappings
docker exec mongo-container mongosh simulator --eval "db.mappings.countDocuments()"

# Check application logs
docker logs api-simulator | grep -i "mapping\|wiremock"

# Manually verify a mapping exists in MongoDB
docker exec mongo-container mongosh simulator --eval "db.mappings.findOne({name: 'Your Mapping Name'})"
```

---

## Best Practices

### **1. Use Read-Only MongoDB Connection**

If possible, use a read-only MongoDB user in CI/CD:

```javascript
// MongoDB user with read-only access
db.createUser({
  user: "ci_readonly",
  pwd: "secure_password",
  roles: [{ role: "read", db: "simulator" }]
})
```

```yaml
env:
  MONGO_URI: mongodb://ci_readonly:secure_password@mongo:27017/simulator
```

### **2. Cache MongoDB Connection in Pipeline**

Reduce startup time by keeping MongoDB warm:

```yaml
# GitHub Actions - keep MongoDB service running across jobs
services:
  mongodb:
    image: mongo:7.0
    options: >-
      --health-cmd "mongosh --eval 'db.adminCommand(\"ping\")'"
      --health-interval 10s
```

### **3. Version Control Mock Exports (Optional)**

Even if using MongoDB, export mocks periodically for backup:

```bash
# Weekly cron job to backup mocks
0 0 * * 0 curl http://localhost:8080/admin/export > backups/mocks-$(date +%Y%m%d).json
```

### **4. Separate Mocks by Environment**

Use MongoDB namespaces to separate environments:

```
- Namespace: "dev" → Development mocks
- Namespace: "staging" → Staging mocks
- Namespace: "production" → Production-like mocks
```

Load specific namespace in CI/CD:

```bash
# For staging tests
export NAMESPACE=staging
curl -X POST "http://localhost:8080/admin/mappings/refresh?namespace=staging"
```

---

## Troubleshooting

### **Issue: No Mappings Loaded**

**Check MongoDB Connection:**
```bash
# Verify MongoDB is accessible
docker exec api-simulator curl http://localhost:8080/actuator/health

# Should show:
{
  "status": "UP",
  "components": {
    "mongo": {
      "status": "UP"
    }
  }
}
```

**Verify Mappings in MongoDB:**
```bash
# Connect to MongoDB and check
docker exec mongo-container mongosh simulator --eval "
  db.mappings.find({enabled: true, deleted: {$ne: true}}).forEach(function(doc) {
    print(doc.name + ' - ' + doc.request.method + ' ' + doc.request.path);
  })
"
```

### **Issue: Mappings in MongoDB but Not Responding**

**Solution: Refresh WireMock**
```bash
curl -X POST http://localhost:8080/admin/mappings/refresh

# Verify WireMock has mappings
curl http://localhost:9999/__admin/mappings
```

### **Issue: Wrong Namespace Loaded**

**Solution: Specify Namespace**
```bash
# Check which namespace you're in
curl http://localhost:8080/auth/current

# Refresh specific namespace
curl -X POST "http://localhost:8080/admin/mappings/refresh?namespace=your-namespace"
```

---

## Summary

### **Your Simplified CI/CD Flow**

```
1. Start API Simulator
   ├─ Connect to existing MongoDB (MONGO_URI)
   └─ Mocks are already there!

2. Refresh WireMock
   ├─ POST /admin/mappings/refresh
   └─ Loads mocks from MongoDB → WireMock

3. Run Tests
   ├─ Point to http://localhost:9999
   └─ Tests hit your pre-created mocks

4. Cleanup
   └─ Stop API Simulator container
```

### **No Import Needed! ✅**

Since your mocks are already in MongoDB:
- ❌ No need to import JSON files
- ❌ No need to version control mock files
- ❌ No need to maintain separate mock configurations
- ✅ Just connect to MongoDB and refresh!

---

**Key Takeaway:**

If mocks are in MongoDB → **Only need to refresh WireMock mappings**

No import required! 🎉

---

**Document Version:** 1.0
**Last Updated:** December 8, 2025
