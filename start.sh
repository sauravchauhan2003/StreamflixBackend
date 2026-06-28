#!/bin/bash
set -e

# JVM Optimizations:
# -Xms64m -Xmx256m: Restrict heap memory footprint
# -XX:+UseSerialGC: Best garbage collector for small containers with single-threaded processing (saves memory overhead compared to G1GC)
# -XX:TieredStopAtLevel=1: Stops JIT compilation at C1, drastically reducing memory usage and speeding up startup time
# -XX:MaxMetaspaceSize=128m: Restricts the class metadata space
JVM_OPTS="-Xms64m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=128m"

echo "Starting Nginx (Frontend on port 80)..."
service nginx start

echo "Starting Eureka Server..."
java $JVM_OPTS -jar /app/eureka-server.jar &

echo "Waiting for Eureka Server to be ready on port 8761..."
while ! nc -z localhost 8761; do   
  sleep 1
done
echo "Eureka Server is up!"

echo "Starting AuthService and VideoService..."
java $JVM_OPTS -jar /app/auth-service.jar &
java $JVM_OPTS -jar /app/video-service.jar &

echo "Waiting for AuthService (9000) and VideoService (8001)..."
while ! nc -z localhost 9000 || ! nc -z localhost 8001; do
  sleep 1
done
echo "AuthService and VideoService are up!"

echo "Starting API Gateway..."
java $JVM_OPTS -jar /app/api-gateway.jar &

echo "Waiting for API Gateway (9010)..."
while ! nc -z localhost 9010; do
  sleep 1
done
echo "API Gateway is up! System is fully started."

echo "All services are running. You can access the application at http://localhost:80"

# Wait for all background jobs so the container doesn't exit
wait
