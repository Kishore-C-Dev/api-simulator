#!/bin/bash

set -e

PROJECT_DIR="/Users/kishorechelekani/Documents/Projects/api-simulator"
cd "$PROJECT_DIR"

echo "Building admin-console..."
cd admin-console && mvn clean package -DskipTests && cd ..

echo "Building mock-server..."
cd mock-server && mvn clean package -DskipTests && cd ..

echo "Starting services with Docker Compose..."
docker-compose up --build -d

echo ""
echo "API Simulator is running!"
echo "  Admin Console: http://localhost:8080"
echo "  Mock Server (default namespace): http://localhost:9999"
echo "  Mock Server management: http://localhost:8090"
echo ""
echo "To add another namespace instance:"
echo "  docker run -p 8091:8090 -p 9998:9999 \\"
echo "    -e SIMULATOR_NAMESPACE=team-alpha \\"
echo "    -e MONGO_URI=mongodb://localhost:27017/simulator \\"
echo "    api-simulator-mock-server-default"
