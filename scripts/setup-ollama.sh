#!/bin/bash

set -e

echo "========================================="
echo "Setting Up Ollama for PatternForge"
echo "========================================="
echo ""

# Check if Docker is running
if ! docker ps > /dev/null 2>&1; then
    echo "❌ Error: Docker is not running"
    exit 1
fi

echo "✓ Docker is running"

# Check if Ollama container exists
if docker ps -a --format '{{.Names}}' | grep -q "^ollama$"; then
    echo "Ollama container exists"
    
    # Start if not running
    if ! docker ps --format '{{.Names}}' | grep -q "^ollama$"; then
        echo "Starting existing Ollama container..."
        docker start ollama
    else
        echo "✓ Ollama container is already running"
    fi
else
    echo "Creating Ollama container..."
    docker run -d \
        --name ollama \
        -p 11434:11434 \
        -v ollama-data:/root/.ollama \
        --restart unless-stopped \
        ollama/ollama:latest
    
    echo "✓ Ollama container created"
fi

# Wait for Ollama to be ready
echo "Waiting for Ollama to be ready..."
for i in {1..30}; do
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo "✓ Ollama is ready"
        break
    fi
    sleep 1
    if [ $i -eq 30 ]; then
        echo "❌ Timeout waiting for Ollama"
        exit 1
    fi
done

# Pull nomic-embed-text model
echo ""
echo "Pulling nomic-embed-text model (768 dimensions)..."
docker exec ollama ollama pull nomic-embed-text

echo ""
echo "========================================="
echo "✓ Ollama setup complete!"
echo "========================================="
echo ""
echo "Ollama Details:"
echo "  - Container: ollama"
echo "  - Port: 11434"
echo "  - Model: nomic-embed-text (768 dims)"
echo "  - API: http://localhost:11434"
echo ""
echo "Test embedding:"
echo "  curl -X POST http://localhost:11434/api/embeddings -d '{\"model\":\"nomic-embed-text\",\"prompt\":\"test\"}'"
echo ""
