# CI/CD Pipeline Integration Guide - API Simulator

**Document Version:** 1.0
**Date:** December 8, 2025
**Target Audience:** DevOps Engineers, CI/CD Architects, Development Teams

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [GitHub Actions Integration](#github-actions-integration)
3. [Jenkins Integration](#jenkins-integration)
4. [GitLab CI/CD Integration](#gitlab-cicd-integration)
5. [Azure DevOps Integration](#azure-devops-integration)
6. [Docker-Based Integration](#docker-based-integration)
7. [Kubernetes Integration](#kubernetes-integration)
8. [Advanced Patterns](#advanced-patterns)
9. [Best Practices](#best-practices)
10. [Troubleshooting](#troubleshooting)

---

## Quick Start

### **3-Step Integration**

1. **Add API Simulator as a service** in your CI/CD pipeline
2. **Load your mock mappings** via REST API or file import
3. **Run your tests** pointing to `http://api-simulator:9999`

### **Minimal Example (GitHub Actions)**

```yaml
# .github/workflows/integration-test.yml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      mongodb:
        image: mongo:7.0
        ports: ["27017:27017"]

      api-simulator:
        image: api-simulator:latest
        ports: ["9999:9999", "8080:8080"]
        env:
          MONGO_URI: mongodb://mongodb:27017/simulator
          AUTH_ENABLED: false

    steps:
      - uses: actions/checkout@v3
      - name: Load Mocks
        run: |
          curl -X POST http://localhost:8080/admin/import \
            -F "file=@tests/mocks/api-mappings.json"
          curl -X POST http://localhost:8080/admin/mappings/refresh

      - name: Run Tests
        run: npm test
        env:
          API_BASE_URL: http://localhost:9999
```

---

## GitHub Actions Integration

### **Complete Example with AI-Generated Mocks**

```yaml
# .github/workflows/ci.yml
name: CI Pipeline with API Simulator

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  API_SIMULATOR_VERSION: latest
  SIMULATOR_PORT: 9999
  ADMIN_PORT: 8080

jobs:
  integration-tests:
    name: Integration Tests
    runs-on: ubuntu-latest

    services:
      mongodb:
        image: mongo:7.0
        ports:
          - 27017:27017
        options: >-
          --health-cmd "mongosh --eval 'db.adminCommand(\"ping\")'"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

      api-simulator:
        image: api-simulator:${{ env.API_SIMULATOR_VERSION }}
        ports:
          - ${{ env.SIMULATOR_PORT }}:9999
          - ${{ env.ADMIN_PORT }}:8080
        env:
          MONGO_URI: mongodb://mongodb:27017/simulator
          AUTH_ENABLED: false
          AI_ENABLED: true
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          AI_MODEL: gpt-4o-mini
        options: >-
          --health-cmd "curl -f http://localhost:8080/actuator/health || exit 1"
          --health-interval 10s
          --health-timeout 5s
          --health-retries 10

    steps:
      - name: Checkout code
        uses: actions/checkout@v3

      - name: Setup Node.js
        uses: actions/setup-node@v3
        with:
          node-version: 18
          cache: 'npm'

      - name: Install dependencies
        run: npm ci

      # Option 1: Import pre-configured mocks
      - name: Import Mock Mappings
        run: |
          echo "Importing mock configurations..."
          curl -X POST http://localhost:${{ env.ADMIN_PORT }}/admin/import \
            -F "file=@tests/fixtures/payment-gateway-mocks.json"

          curl -X POST http://localhost:${{ env.ADMIN_PORT }}/admin/import \
            -F "file=@tests/fixtures/user-service-mocks.json"

      # Option 2: Generate mocks from OpenAPI spec using AI
      - name: Generate Mocks from OpenAPI
        if: env.AI_ENABLED == 'true'
        run: |
          echo "Generating mocks from OpenAPI spec..."
          curl -X POST http://localhost:${{ env.ADMIN_PORT }}/api/ai/generate-from-spec \
            -H "Content-Type: application/json" \
            --data-binary @api-spec/openapi.yaml \
            -o generated-mocks.json

          # Import the generated mocks
          curl -X POST http://localhost:${{ env.ADMIN_PORT }}/admin/import \
            -F "file=@generated-mocks.json"

      - name: Refresh WireMock Mappings
        run: |
          curl -X POST http://localhost:${{ env.ADMIN_PORT }}/admin/mappings/refresh
          echo "WireMock mappings loaded successfully"

      - name: Verify Mocks Loaded
        run: |
          # Check that endpoints are responding
          curl -f http://localhost:${{ env.SIMULATOR_PORT }}/health || exit 1
          echo "Mock endpoints verified"

      - name: Run Unit Tests
        run: npm run test:unit

      - name: Run Integration Tests
        run: npm run test:integration
        env:
          PAYMENT_API_URL: http://localhost:${{ env.SIMULATOR_PORT }}
          USER_API_URL: http://localhost:${{ env.SIMULATOR_PORT }}
          ORDER_API_URL: http://localhost:${{ env.SIMULATOR_PORT }}

      - name: Run E2E Tests
        run: npm run test:e2e
        env:
          API_BASE_URL: http://localhost:${{ env.SIMULATOR_PORT }}

      - name: Publish Test Results
        uses: dorny/test-reporter@v1
        if: always()
        with:
          name: Integration Test Results
          path: test-results/*.xml
          reporter: jest-junit

      - name: Upload Coverage
        uses: codecov/codecov-action@v3
        with:
          files: ./coverage/coverage-final.json
```

### **Using Docker Compose in GitHub Actions**

```yaml
# .github/workflows/docker-compose-test.yml
name: Tests with Docker Compose

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v3

      - name: Create docker-compose.test.yml
        run: |
          cat > docker-compose.test.yml <<EOF
          version: '3.8'
          services:
            mongodb:
              image: mongo:7.0
              ports: ["27017:27017"]
              healthcheck:
                test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
                interval: 10s
                timeout: 5s
                retries: 5

            api-simulator:
              image: api-simulator:latest
              ports: ["8080:8080", "9999:9999"]
              depends_on:
                mongodb:
                  condition: service_healthy
              environment:
                MONGO_URI: mongodb://mongodb:27017/simulator
                AUTH_ENABLED: false
              healthcheck:
                test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
                interval: 10s
                timeout: 5s
                retries: 10
          EOF

      - name: Start Services
        run: docker-compose -f docker-compose.test.yml up -d

      - name: Wait for API Simulator
        run: |
          timeout 60 sh -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'

      - name: Load Mocks
        run: |
          curl -X POST http://localhost:8080/admin/import \
            -F "file=@tests/mocks/api-mappings.json"
          curl -X POST http://localhost:8080/admin/mappings/refresh

      - name: Run Tests
        run: npm test

      - name: Cleanup
        if: always()
        run: docker-compose -f docker-compose.test.yml down -v
```

---

## Jenkins Integration

### **Declarative Pipeline**

```groovy
// Jenkinsfile
pipeline {
    agent any

    environment {
        API_SIMULATOR_IMAGE = 'api-simulator:latest'
        SIMULATOR_CONTAINER = "api-simulator-${BUILD_NUMBER}"
        MONGO_CONTAINER = "mongo-${BUILD_NUMBER}"
        SIMULATOR_URL = "http://api-simulator:9999"
        ADMIN_URL = "http://api-simulator:8080"
    }

    stages {
        stage('Setup') {
            steps {
                script {
                    // Create Docker network
                    sh "docker network create test-network-${BUILD_NUMBER} || true"

                    // Start MongoDB
                    sh """
                        docker run -d --name ${MONGO_CONTAINER} \
                          --network test-network-${BUILD_NUMBER} \
                          -p 27017:27017 \
                          mongo:7.0
                    """

                    // Start API Simulator
                    sh """
                        docker run -d --name ${SIMULATOR_CONTAINER} \
                          --network test-network-${BUILD_NUMBER} \
                          -p 8080:8080 \
                          -p 9999:9999 \
                          -e MONGO_URI=mongodb://${MONGO_CONTAINER}:27017/simulator \
                          -e AUTH_ENABLED=false \
                          ${API_SIMULATOR_IMAGE}
                    """

                    // Wait for health check
                    sh """
                        timeout 60 sh -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'
                    """
                }
            }
        }

        stage('Load Mock Configurations') {
            steps {
                script {
                    // Import mock mappings
                    sh """
                        curl -X POST http://localhost:8080/admin/import \
                          -F "file=@tests/mocks/payment-api.json"

                        curl -X POST http://localhost:8080/admin/import \
                          -F "file=@tests/mocks/user-api.json"
                    """

                    // Refresh WireMock
                    sh "curl -X POST http://localhost:8080/admin/mappings/refresh"
                }
            }
        }

        stage('Integration Tests') {
            steps {
                sh '''
                    export PAYMENT_API_URL=http://localhost:9999
                    export USER_API_URL=http://localhost:9999
                    mvn test -Dtest=IntegrationTest
                '''
            }
        }

        stage('Performance Tests') {
            steps {
                sh '''
                    export TARGET_URL=http://localhost:9999
                    mvn gatling:test
                '''
            }
        }
    }

    post {
        always {
            // Cleanup containers and network
            sh """
                docker stop ${SIMULATOR_CONTAINER} ${MONGO_CONTAINER} || true
                docker rm ${SIMULATOR_CONTAINER} ${MONGO_CONTAINER} || true
                docker network rm test-network-${BUILD_NUMBER} || true
            """

            // Publish test results
            junit '**/target/surefire-reports/*.xml'
            gatlingArchive()
        }

        success {
            echo 'Integration tests passed!'
        }

        failure {
            echo 'Integration tests failed!'
            // Optional: Export logs for debugging
            sh "docker logs ${SIMULATOR_CONTAINER} > api-simulator-${BUILD_NUMBER}.log || true"
            archiveArtifacts artifacts: "*.log", allowEmptyArchive: true
        }
    }
}
```

### **Scripted Pipeline with Kubernetes**

```groovy
// Jenkinsfile.k8s
node {
    def namespace = "test-${BUILD_NUMBER}"

    stage('Deploy to Kubernetes') {
        sh """
            kubectl create namespace ${namespace}

            # Deploy MongoDB
            kubectl apply -n ${namespace} -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mongodb
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mongodb
  template:
    metadata:
      labels:
        app: mongodb
    spec:
      containers:
      - name: mongodb
        image: mongo:7.0
        ports:
        - containerPort: 27017
---
apiVersion: v1
kind: Service
metadata:
  name: mongodb
spec:
  selector:
    app: mongodb
  ports:
  - port: 27017
EOF

            # Deploy API Simulator
            kubectl apply -n ${namespace} -f - <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-simulator
spec:
  replicas: 1
  selector:
    matchLabels:
      app: api-simulator
  template:
    metadata:
      labels:
        app: api-simulator
    spec:
      containers:
      - name: api-simulator
        image: api-simulator:latest
        ports:
        - containerPort: 8080
        - containerPort: 9999
        env:
        - name: MONGO_URI
          value: mongodb://mongodb:27017/simulator
        - name: AUTH_ENABLED
          value: "false"
---
apiVersion: v1
kind: Service
metadata:
  name: api-simulator
spec:
  selector:
    app: api-simulator
  ports:
  - name: admin
    port: 8080
  - name: wiremock
    port: 9999
EOF
        """

        // Wait for pods to be ready
        sh """
            kubectl wait --for=condition=ready pod -l app=api-simulator -n ${namespace} --timeout=300s
        """
    }

    stage('Load Mocks') {
        sh """
            kubectl port-forward -n ${namespace} svc/api-simulator 8080:8080 &
            PF_PID=\$!

            sleep 5

            curl -X POST http://localhost:8080/admin/import \
              -F "file=@tests/mocks/api-mappings.json"

            curl -X POST http://localhost:8080/admin/mappings/refresh

            kill \$PF_PID
        """
    }

    stage('Run Tests') {
        sh """
            kubectl port-forward -n ${namespace} svc/api-simulator 9999:9999 &
            PF_PID=\$!

            export API_BASE_URL=http://localhost:9999
            mvn test

            kill \$PF_PID
        """
    }

    stage('Cleanup') {
        sh "kubectl delete namespace ${namespace}"
    }
}
```

---

## GitLab CI/CD Integration

### **Complete .gitlab-ci.yml**

```yaml
# .gitlab-ci.yml
stages:
  - setup
  - test
  - cleanup

variables:
  SIMULATOR_IMAGE: api-simulator:latest
  MONGO_IMAGE: mongo:7.0
  NAMESPACE: test-$CI_COMMIT_SHORT_SHA

services:
  - name: $MONGO_IMAGE
    alias: mongodb
  - name: $SIMULATOR_IMAGE
    alias: api-simulator
    variables:
      MONGO_URI: mongodb://mongodb:27017/simulator
      AUTH_ENABLED: "false"

before_script:
  - apk add --no-cache curl jq
  - echo "Waiting for API Simulator..."
  - timeout 60 sh -c 'until curl -f http://api-simulator:8080/actuator/health; do sleep 2; done'

setup:mocks:
  stage: setup
  script:
    - echo "Loading mock configurations..."
    - |
      curl -X POST http://api-simulator:8080/admin/import \
        -F "file=@tests/mocks/payment-api.json"
    - |
      curl -X POST http://api-simulator:8080/admin/import \
        -F "file=@tests/mocks/user-api.json"
    - curl -X POST http://api-simulator:8080/admin/mappings/refresh
    - echo "Mocks loaded successfully"

unit-tests:
  stage: test
  script:
    - npm run test:unit

integration-tests:
  stage: test
  dependencies:
    - setup:mocks
  script:
    - export PAYMENT_API_URL=http://api-simulator:9999
    - export USER_API_URL=http://api-simulator:9999
    - npm run test:integration
  artifacts:
    reports:
      junit: test-results/*.xml

e2e-tests:
  stage: test
  dependencies:
    - setup:mocks
  script:
    - export API_BASE_URL=http://api-simulator:9999
    - npm run test:e2e
  artifacts:
    when: always
    paths:
      - cypress/videos/
      - cypress/screenshots/
```

### **Kubernetes-Native GitLab CI**

```yaml
# .gitlab-ci.yml with Kubernetes
stages:
  - deploy
  - test
  - cleanup

variables:
  NAMESPACE: test-$CI_COMMIT_SHORT_SHA

deploy:simulator:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl create namespace $NAMESPACE
    - |
      kubectl apply -n $NAMESPACE -f - <<EOF
      apiVersion: v1
      kind: ConfigMap
      metadata:
        name: api-mocks
      data:
        payment-api.json: |
          $(cat tests/mocks/payment-api.json)
        user-api.json: |
          $(cat tests/mocks/user-api.json)
      ---
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: mongodb
      spec:
        replicas: 1
        selector:
          matchLabels:
            app: mongodb
        template:
          metadata:
            labels:
              app: mongodb
          spec:
            containers:
            - name: mongodb
              image: mongo:7.0
              ports:
              - containerPort: 27017
      ---
      apiVersion: v1
      kind: Service
      metadata:
        name: mongodb
      spec:
        selector:
          app: mongodb
        ports:
        - port: 27017
      ---
      apiVersion: apps/v1
      kind: Deployment
      metadata:
        name: api-simulator
      spec:
        replicas: 1
        selector:
          matchLabels:
            app: api-simulator
        template:
          metadata:
            labels:
              app: api-simulator
          spec:
            containers:
            - name: api-simulator
              image: $SIMULATOR_IMAGE
              ports:
              - containerPort: 8080
              - containerPort: 9999
              env:
              - name: MONGO_URI
                value: mongodb://mongodb:27017/simulator
              - name: AUTH_ENABLED
                value: "false"
              volumeMounts:
              - name: mocks
                mountPath: /mocks
            volumes:
            - name: mocks
              configMap:
                name: api-mocks
      ---
      apiVersion: v1
      kind: Service
      metadata:
        name: api-simulator
      spec:
        selector:
          app: api-simulator
        ports:
        - name: admin
          port: 8080
        - name: wiremock
          port: 9999
      EOF
    - kubectl wait --for=condition=ready pod -l app=api-simulator -n $NAMESPACE --timeout=300s

test:integration:
  stage: test
  image: node:18
  script:
    - export API_BASE_URL=http://api-simulator.$NAMESPACE.svc.cluster.local:9999
    - npm test
  dependencies:
    - deploy:simulator

cleanup:
  stage: cleanup
  image: bitnami/kubectl:latest
  when: always
  script:
    - kubectl delete namespace $NAMESPACE
```

---

## Azure DevOps Integration

### **azure-pipelines.yml**

```yaml
# azure-pipelines.yml
trigger:
  - main
  - develop

pool:
  vmImage: 'ubuntu-latest'

variables:
  SIMULATOR_IMAGE: 'api-simulator:latest'
  SIMULATOR_PORT: 9999
  ADMIN_PORT: 8080

stages:
  - stage: Test
    displayName: 'Run Integration Tests'
    jobs:
      - job: IntegrationTests
        displayName: 'Integration Tests with API Simulator'

        services:
          mongodb:
            image: mongo:7.0
            ports:
              - 27017:27017

          api-simulator:
            image: $(SIMULATOR_IMAGE)
            ports:
              - $(ADMIN_PORT):8080
              - $(SIMULATOR_PORT):9999
            env:
              MONGO_URI: mongodb://mongodb:27017/simulator
              AUTH_ENABLED: false

        steps:
          - checkout: self

          - task: NodeTool@0
            inputs:
              versionSpec: '18.x'
            displayName: 'Install Node.js'

          - script: npm ci
            displayName: 'Install dependencies'

          - script: |
              echo "Waiting for API Simulator..."
              timeout 60 sh -c 'until curl -f http://localhost:$(ADMIN_PORT)/actuator/health; do sleep 2; done'
            displayName: 'Wait for API Simulator'

          - script: |
              curl -X POST http://localhost:$(ADMIN_PORT)/admin/import \
                -F "file=@tests/mocks/api-mappings.json"
              curl -X POST http://localhost:$(ADMIN_PORT)/admin/mappings/refresh
            displayName: 'Load Mock Mappings'

          - script: npm test
            env:
              API_BASE_URL: http://localhost:$(SIMULATOR_PORT)
            displayName: 'Run Integration Tests'

          - task: PublishTestResults@2
            condition: always()
            inputs:
              testResultsFormat: 'JUnit'
              testResultsFiles: '**/test-results/*.xml'
            displayName: 'Publish Test Results'

          - task: PublishCodeCoverageResults@1
            inputs:
              codeCoverageTool: 'Cobertura'
              summaryFileLocation: '$(System.DefaultWorkingDirectory)/**/coverage/cobertura-coverage.xml'
            displayName: 'Publish Coverage'
```

---

## Docker-Based Integration

### **Standalone Docker Script**

```bash
#!/bin/bash
# run-integration-tests.sh

set -e

# Configuration
SIMULATOR_IMAGE="api-simulator:latest"
MONGO_CONTAINER="mongo-test-$$"
SIMULATOR_CONTAINER="api-simulator-test-$$"
NETWORK="test-network-$$"

# Cleanup function
cleanup() {
    echo "Cleaning up..."
    docker stop $SIMULATOR_CONTAINER $MONGO_CONTAINER 2>/dev/null || true
    docker rm $SIMULATOR_CONTAINER $MONGO_CONTAINER 2>/dev/null || true
    docker network rm $NETWORK 2>/dev/null || true
}

# Trap cleanup on exit
trap cleanup EXIT

# Create network
echo "Creating Docker network..."
docker network create $NETWORK

# Start MongoDB
echo "Starting MongoDB..."
docker run -d --name $MONGO_CONTAINER \
  --network $NETWORK \
  -p 27017:27017 \
  mongo:7.0

# Start API Simulator
echo "Starting API Simulator..."
docker run -d --name $SIMULATOR_CONTAINER \
  --network $NETWORK \
  -p 8080:8080 \
  -p 9999:9999 \
  -e MONGO_URI=mongodb://$MONGO_CONTAINER:27017/simulator \
  -e AUTH_ENABLED=false \
  $SIMULATOR_IMAGE

# Wait for health check
echo "Waiting for API Simulator to be ready..."
timeout 60 sh -c 'until curl -f http://localhost:8080/actuator/health; do sleep 2; done'

# Load mock mappings
echo "Loading mock mappings..."
curl -X POST http://localhost:8080/admin/import \
  -F "file=@tests/mocks/payment-api.json"

curl -X POST http://localhost:8080/admin/import \
  -F "file=@tests/mocks/user-api.json"

curl -X POST http://localhost:8080/admin/mappings/refresh

# Run tests
echo "Running integration tests..."
export API_BASE_URL=http://localhost:9999
npm test

echo "Tests completed successfully!"
```

### **docker-compose.test.yml**

```yaml
# docker-compose.test.yml
version: '3.8'

services:
  mongodb:
    image: mongo:7.0
    ports:
      - "27017:27017"
    healthcheck:
      test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - test-network

  api-simulator:
    image: api-simulator:latest
    build:
      context: ./backend
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
      - "9999:9999"
    depends_on:
      mongodb:
        condition: service_healthy
    environment:
      MONGO_URI: mongodb://mongodb:27017/simulator
      AUTH_ENABLED: false
      AI_ENABLED: ${AI_ENABLED:-false}
      OPENAI_API_KEY: ${OPENAI_API_KEY:-}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 10
    volumes:
      - ./tests/mocks:/mocks:ro
    networks:
      - test-network

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
      ADMIN_URL: http://api-simulator:8080
    networks:
      - test-network
    command: >
      sh -c "
        echo 'Loading mock mappings...' &&
        curl -X POST http://api-simulator:8080/admin/import -F 'file=@/mocks/payment-api.json' &&
        curl -X POST http://api-simulator:8080/admin/mappings/refresh &&
        echo 'Running tests...' &&
        npm test
      "

networks:
  test-network:
    driver: bridge
```

**Usage:**
```bash
docker-compose -f docker-compose.test.yml up --abort-on-container-exit --exit-code-from test-runner
```

---

## Kubernetes Integration

### **Helm Chart for CI/CD**

```yaml
# helm/api-simulator-test/values.yaml
replicaCount: 1

image:
  repository: api-simulator
  tag: latest
  pullPolicy: IfNotPresent

mongodb:
  enabled: true
  image: mongo:7.0

service:
  type: ClusterIP
  adminPort: 8080
  wireMockPort: 9999

env:
  - name: MONGO_URI
    value: "mongodb://{{ .Release.Name }}-mongodb:27017/simulator"
  - name: AUTH_ENABLED
    value: "false"

resources:
  limits:
    cpu: 500m
    memory: 1Gi
  requests:
    cpu: 250m
    memory: 512Mi

healthCheck:
  enabled: true
  path: /actuator/health
```

```yaml
# helm/api-simulator-test/templates/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "api-simulator.fullname" . }}
spec:
  replicas: {{ .Values.replicaCount }}
  selector:
    matchLabels:
      app: {{ include "api-simulator.name" . }}
  template:
    metadata:
      labels:
        app: {{ include "api-simulator.name" . }}
    spec:
      containers:
      - name: api-simulator
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        ports:
        - name: admin
          containerPort: 8080
        - name: wiremock
          containerPort: 9999
        env:
        {{- range .Values.env }}
        - name: {{ .name }}
          value: {{ .value | quote }}
        {{- end }}
        {{- if .Values.healthCheck.enabled }}
        livenessProbe:
          httpGet:
            path: {{ .Values.healthCheck.path }}
            port: admin
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: {{ .Values.healthCheck.path }}
            port: admin
          initialDelaySeconds: 10
          periodSeconds: 5
        {{- end }}
        resources:
          {{- toYaml .Values.resources | nindent 10 }}
```

**Deploy in CI/CD:**
```bash
# Install Helm chart
helm install api-simulator-test ./helm/api-simulator-test \
  --namespace test-$BUILD_NUMBER \
  --create-namespace \
  --wait --timeout 5m

# Load mocks
kubectl run -it --rm load-mocks \
  --image=curlimages/curl \
  --namespace test-$BUILD_NUMBER \
  -- sh -c "
    curl -X POST http://api-simulator-test:8080/admin/import \
      -F 'file=@/mocks/api-mappings.json' \
      --upload-file /mocks/api-mappings.json
  "

# Run tests with port-forward
kubectl port-forward svc/api-simulator-test 9999:9999 -n test-$BUILD_NUMBER &
export API_BASE_URL=http://localhost:9999
npm test

# Cleanup
helm uninstall api-simulator-test -n test-$BUILD_NUMBER
kubectl delete namespace test-$BUILD_NUMBER
```

---

## Advanced Patterns

### **Pattern 1: Pre-Seeded Docker Image**

```dockerfile
# Dockerfile.test
FROM api-simulator:latest

# Copy test mock configurations
COPY tests/mocks /app/mocks

# Add startup script to auto-load mocks
COPY load-mocks.sh /app/load-mocks.sh
RUN chmod +x /app/load-mocks.sh

CMD ["sh", "-c", "/app/load-mocks.sh && java -jar /app/api-simulator.jar"]
```

```bash
# load-mocks.sh
#!/bin/sh
set -e

echo "Waiting for MongoDB..."
while ! nc -z mongodb 27017; do
  sleep 1
done

echo "Starting API Simulator with auto-load..."
java -jar /app/api-simulator.jar &
APP_PID=$!

echo "Waiting for API Simulator..."
while ! curl -f http://localhost:8080/actuator/health; do
  sleep 2
done

echo "Loading mocks..."
for file in /app/mocks/*.json; do
  curl -X POST http://localhost:8080/admin/import -F "file=@$file"
done

curl -X POST http://localhost:8080/admin/mappings/refresh

echo "Mocks loaded. API Simulator ready."
wait $APP_PID
```

### **Pattern 2: AI-Generated Mocks in Pipeline**

```yaml
# .github/workflows/ai-mocks.yml
- name: Generate Mocks from API Specs
  env:
    OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
  run: |
    # Generate mocks from OpenAPI specs using AI
    curl -X POST http://localhost:8080/api/ai/generate-from-spec \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $OPENAI_API_KEY" \
      --data-binary @specs/payment-api.yaml \
      -o payment-mocks.json

    curl -X POST http://localhost:8080/api/ai/generate-from-spec \
      -H "Content-Type: application/json" \
      -H "Authorization: Bearer $OPENAI_API_KEY" \
      --data-binary @specs/user-api.yaml \
      -o user-mocks.json

    # Import generated mocks
    curl -X POST http://localhost:8080/admin/import -F "file=@payment-mocks.json"
    curl -X POST http://localhost:8080/admin/import -F "file=@user-mocks.json"
    curl -X POST http://localhost:8080/admin/mappings/refresh
```

### **Pattern 3: Parallel Test Execution**

```yaml
# .github/workflows/parallel-tests.yml
strategy:
  matrix:
    test-suite:
      - payment-tests
      - user-tests
      - order-tests
    include:
      - test-suite: payment-tests
        mock-file: payment-api.json
        test-command: npm run test:payment
      - test-suite: user-tests
        mock-file: user-api.json
        test-command: npm run test:user
      - test-suite: order-tests
        mock-file: order-api.json
        test-command: npm run test:order

steps:
  - name: Start API Simulator for ${{ matrix.test-suite }}
    run: |
      docker run -d --name api-simulator-${{ matrix.test-suite }} \
        -p 9999:9999 -p 8080:8080 \
        -e MONGO_URI=mongodb://mongodb:27017/simulator \
        api-simulator:latest

  - name: Load ${{ matrix.mock-file }}
    run: |
      curl -X POST http://localhost:8080/admin/import \
        -F "file=@tests/mocks/${{ matrix.mock-file }}"
      curl -X POST http://localhost:8080/admin/mappings/refresh

  - name: Run ${{ matrix.test-command }}
    run: ${{ matrix.test-command }}
```

---

## Best Practices

### **1. Mock Configuration Management**

✅ **DO:**
- Store mock configurations in version control (`tests/mocks/*.json`)
- Use descriptive names for mock files (`payment-gateway-success.json`)
- Document expected API behavior in mock files
- Use AI to generate mocks from OpenAPI specs
- Export/import configurations between environments

❌ **DON'T:**
- Hardcode mock data in test files
- Share mock configurations across unrelated test suites
- Store sensitive data in mock responses

### **2. Pipeline Performance**

✅ **Optimize:**
- Use Docker layer caching for faster builds
- Run API Simulator as a service (not separate job)
- Load only required mocks per test suite
- Use health checks to avoid race conditions
- Cache dependencies (npm, Maven, etc.)

**Example Caching:**
```yaml
- name: Cache Maven packages
  uses: actions/cache@v3
  with:
    path: ~/.m2
    key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}
    restore-keys: ${{ runner.os }}-m2
```

### **3. Error Handling**

```bash
# Robust error handling in shell scripts
set -e  # Exit on error
set -u  # Error on undefined variable
set -o pipefail  # Catch errors in pipes

# Always cleanup on exit
trap cleanup EXIT
cleanup() {
    docker stop api-simulator || true
    docker rm api-simulator || true
}
```

### **4. Test Isolation**

```yaml
# Unique namespace/container per build
variables:
  NAMESPACE: test-${{ github.run_id }}
  CONTAINER_NAME: api-simulator-${{ github.run_id }}
```

### **5. Monitoring & Debugging**

```yaml
- name: Capture Logs on Failure
  if: failure()
  run: |
    docker logs api-simulator > api-simulator.log
    curl http://localhost:8080/admin/mappings | jq . > loaded-mappings.json

- name: Upload Logs
  if: failure()
  uses: actions/upload-artifact@v3
  with:
    name: debug-logs
    path: |
      api-simulator.log
      loaded-mappings.json
```

---

## Troubleshooting

### **Common Issues**

#### **1. "Connection Refused" Errors**

**Problem:** Tests can't connect to API Simulator

**Solutions:**
```bash
# Check if simulator is running
curl -f http://localhost:8080/actuator/health

# Check if mappings are loaded
curl http://localhost:8080/admin/mappings | jq '.[] | .name'

# Check WireMock port
curl http://localhost:9999/__admin/mappings

# Verify Docker network
docker network inspect test-network

# Check service health in Kubernetes
kubectl get pods -l app=api-simulator
kubectl logs -l app=api-simulator
```

#### **2. "No Stub Mappings" Error**

**Problem:** WireMock returns "No response could be served"

**Solutions:**
```bash
# Refresh WireMock mappings
curl -X POST http://localhost:8080/admin/mappings/refresh

# Verify mappings in MongoDB
docker exec mongo-container mongosh simulator --eval "db.mappings.find().pretty()"

# Check import logs
docker logs api-simulator | grep "import"
```

#### **3. Timeout Waiting for Health Check**

**Problem:** Health check never becomes healthy

**Solutions:**
```yaml
# Increase timeout
healthcheck:
  interval: 10s
  timeout: 10s  # Increase from 5s
  retries: 20   # Increase from 10
  start_period: 40s  # Add startup grace period

# Check startup logs
docker logs api-simulator --tail 50

# Verify MongoDB connection
docker exec api-simulator curl -f http://localhost:8080/actuator/health
```

#### **4. Port Conflicts**

**Problem:** Port 8080 or 9999 already in use

**Solutions:**
```yaml
# Use dynamic port mapping
services:
  api-simulator:
    ports:
      - "0:8080"  # Random port mapped to 8080
      - "0:9999"  # Random port mapped to 9999

# Or use different ports
    ports:
      - "18080:8080"
      - "19999:9999"
```

---

## Summary

### **Quick Integration Checklist**

- [ ] Add API Simulator as a Docker service in your pipeline
- [ ] Configure MongoDB dependency
- [ ] Add health checks with appropriate timeouts
- [ ] Create mock configuration files in `tests/mocks/`
- [ ] Load mocks via REST API before tests
- [ ] Configure test environment variables to point to simulator
- [ ] Add cleanup steps to remove containers/namespaces
- [ ] Implement error handling and logging
- [ ] Optimize for parallel execution if needed
- [ ] Document mock configurations for your team

### **Key Takeaways**

1. **API Simulator integrates seamlessly** with all major CI/CD platforms
2. **Docker-first approach** simplifies deployment and cleanup
3. **Health checks are critical** to avoid race conditions
4. **AI capabilities** can auto-generate mocks from OpenAPI specs
5. **Proper cleanup** prevents resource leaks in CI/CD
6. **Mock versioning** in Git ensures reproducible tests

---

**Need Help?**
- GitHub Issues: https://github.com/your-org/api-simulator/issues
- Documentation: CLAUDE.md, README.md
- Examples: See `examples/ci-cd/` directory

**Document Version:** 1.0
**Last Updated:** December 8, 2025
**Maintained By:** API Simulator Team
