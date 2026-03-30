#!/bin/bash
set -e

echo "=== Webhook Notifier System — Setup ==="

echo "Building Java Modules..."
./gradlew clean build -DskipTests

echo "Starting Infrastructure (Kafka, Postgres, Redis, Mock Receiver)..."
docker-compose up -d zookeeper kafka postgres redis receiver prometheus grafana --build

echo "Waiting for Infrastructure to initialize..."
sleep 10

echo "Create topics events with partitions and replicas"
docker-compose exec kafka kafka-topics --create --bootstrap-server localhost:9092 --topic events --partitions 2 --replication-factor 1

# Update 
# docker compose exec kafka kafka-topics --alter --bootstrap-server localhost:9092 --topic events --partitions 2 --replication-factor 1


echo "Starting Webhook Workers (Scaled to 2 instances for high throughput)..."
docker-compose up -d --scale webhook-worker=2 webhook-worker --build

#echo "Starting Mock Publisher to generate events..."
docker-compose up -d publisher --build

