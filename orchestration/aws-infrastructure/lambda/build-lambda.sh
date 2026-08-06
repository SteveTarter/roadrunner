#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

# Change directory to the script's directory to ensure relative paths work
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "📦 Building AWS Lambda Package..."
echo "=================================================="

# Check for npm
if ! command -v npm &> /dev/null; then
    echo "❌ Error: npm is not installed. Please install it first."
    exit 1
fi

# Check for zip
if ! command -v zip &> /dev/null; then
    echo "❌ Error: zip command is not available. Installing via apt-get or similar is required."
    exit 1
fi

# Clean up previous zip
if [ -f "../lambda.zip" ]; then
    echo "🧹 Removing old lambda.zip..."
    rm "../lambda.zip"
fi

# Install dependencies (only production)
echo "📦 Installing production dependencies..."
npm install --production

# Create ZIP archive
echo "🤐 Creating zip file: lambda.zip..."
zip -r ../lambda.zip index.js package.json node_modules/ > /dev/null

echo "✅ Lambda bundle built successfully at: orchestration/aws-infrastructure/lambda.zip"
echo "=================================================="
