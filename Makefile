JAVA_HOME := /usr/lib/jvm/java-21-openjdk-amd64
export JAVA_HOME

# Run unit tests for a specific module (default: core)
# Usage: make test or make test MODULE=sagaweaw-spring
MODULE ?= sagaweaw-core
test:
	./mvnw test -pl $(MODULE) --no-transfer-progress

# Run full build: compile + unit tests + integration tests + quality gates
verify:
	./mvnw verify --no-transfer-progress

# Compile all modules without running tests
build:
	./mvnw compile --no-transfer-progress

# Remove all build artifacts
clean:
	./mvnw clean --no-transfer-progress

# Run the order example backend (port 8080) + dashboard dev server (port 8484)
# Open http://localhost:8484 after both are up
dev-backend:
	./mvnw spring-boot:run -pl sagaweaw-examples/order-saga-example --no-transfer-progress

dev-dash:
	cd sagaweaw-dashboard && npm run dev

.PHONY: test verify build clean dev-backend dev-dash
