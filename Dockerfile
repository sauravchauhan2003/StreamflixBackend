# Stage 1: Build the Microservices
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /build

# Copy only backend source code to prevent frontend changes from breaking Maven cache
COPY Eureka-Server/ ./Eureka-Server/
COPY API-Gateway/ ./API-Gateway/
COPY AuthService/ ./AuthService/
COPY VideoService/ ./VideoService/

# Build Eureka-Server
WORKDIR /build/Eureka-Server
RUN mvn clean package -DskipTests

# Build API-Gateway
WORKDIR /build/API-Gateway
RUN mvn clean package -DskipTests

# Build AuthService
WORKDIR /build/AuthService
RUN mvn clean package -DskipTests

# Build VideoService
WORKDIR /build/VideoService
RUN mvn clean package -DskipTests

# Stage 2: Create the Runtime Image
# Using Nvidia CUDA base image for optional GPU transcoding support, combined with Ubuntu 22.04
FROM nvidia/cuda:12.2.2-base-ubuntu22.04

# Prevent interactive prompts during apt-get
ENV DEBIAN_FRONTEND=noninteractive

# Install Java 21, FFmpeg (with dynamic NVENC support if GPU is present), Nginx, and Netcat
RUN apt-get update && \
    apt-get install -y openjdk-21-jre-headless nginx ffmpeg netcat-openbsd curl wget dos2unix && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy JARs from the build stage
COPY --from=build /build/Eureka-Server/target/*.jar /app/eureka-server.jar
COPY --from=build /build/API-Gateway/target/*.jar /app/api-gateway.jar
COPY --from=build /build/AuthService/target/*.jar /app/auth-service.jar
COPY --from=build /build/VideoService/target/*.jar /app/video-service.jar

# Copy frontend to Nginx default directory
COPY frontend/ /var/www/html/

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/sites-available/default

# Copy the startup script
COPY start.sh /app/start.sh

# Ensure start.sh has linux line endings (in case it was created on Windows) and is executable
RUN dos2unix /app/start.sh && chmod +x /app/start.sh

# Expose ports
# 80: Frontend
# 9010: API Gateway
# 9000: Auth Service
# 8001: Video Service
# 8761: Eureka Server
EXPOSE 80 9010 9000 8001 8761

# Start the application orchestrator
ENTRYPOINT ["/app/start.sh"]
