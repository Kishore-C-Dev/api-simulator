#!/bin/bash

set -e

echo "🚀 Building and Running API Simulator..."
echo "======================================"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker and try again."
    exit 1
fi

# Navigate to project directory
PROJECT_DIR="/Users/kishorechelekani/Documents/Projects/api-simulator"
cd "$PROJECT_DIR"

echo "📁 Current directory: $(pwd)"

# Build the Spring Boot application
echo "🔨 Building Spring Boot application..."
cd backend
mvn clean package -DskipTests
cd ..

echo "✅ Spring Boot application built successfully!"

# Start the services with Docker Compose
echo "🐳 Starting services with Docker Compose..."
docker-compose up --build -d

echo "⏳ Waiting for services to start..."
sleep 10

# Check service status
echo "📊 Checking service status..."
docker-compose ps

echo ""
echo "🎉 API Simulator is now running!"
echo "================================"
echo "📱 Web Interface: http://localhost:8080"
echo "🔧 WireMock API:  http://localhost:9999"
echo "📋 Admin API:     http://localhost:8080/admin"
echo "🩺 Health Check:  http://localhost:8080/actuator/health"
echo ""
echo "🧪 Test the seeded endpoints:"
echo "   curl http://localhost:9999/hello"
echo "   curl -X POST http://localhost:9999/orders -H 'Content-Type: application/json' -d '{\"item\":\"test\"}'"
echo "   curl http://localhost:9999/flaky"
echo ""
echo "📖 View logs: docker-compose logs -f"
echo "🛑 Stop services: docker-compose down"