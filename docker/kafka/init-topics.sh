#!/bin/bash
# Wait for Kafka to become healthy
echo "Waiting for Kafka to be ready..."
cub kafka-ready -b kafka:29092 1 30

# Create application topics with retention policies
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 --partitions 3 --replication-factor 1 --topic booking-events
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 --partitions 3 --replication-factor 1 --topic payment-events
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 --partitions 3 --replication-factor 1 --topic notification-events
kafka-topics --create --if-not-exists --bootstrap-server kafka:29092 --partitions 3 --replication-factor 1 --topic parking-lot-events

echo "Kafka topics created successfully."
