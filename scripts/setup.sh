#!/bin/bash
set -e

echo "=== Webhook Notifier System — Setup ==="

echo "Building Java Modules..."
./gradlew clean build -DskipTests

echo "Rebuild services"
docker compose build --no-cache

echo "Starting Infrastructure (Kafka, Postgres, Redis, Mock Receiver)..."
docker-compose up -d zookeeper kafka postgres redis receiver

echo "Waiting for Infrastructure to initialize..."
sleep 10

echo "Starting Webhook Workers (Scaled to 2 instances for high throughput)..."
docker-compose up -d --scale webhook-worker=2 webhook-worker

echo "Starting Mock Publisher to generate events..."
docker-compose up -d publisher

