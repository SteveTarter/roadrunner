#!/usr/bin/env bash

# Exit immediately if a command exits with a non-zero status
set -e

# Change directory to the script's directory to ensure relative paths work
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=================================================="
echo "🚀 Roadrunner Frontend Deployment Script"
echo "=================================================="

# ------------------------------------------------------------------------------
# 1. Validation & Setup
# ------------------------------------------------------------------------------

# Check for AWS CLI
if ! command -v aws &> /dev/null; then
    echo "❌ Error: AWS CLI is not installed. Please install it first."
    exit 1
fi

# Check for Terraform
if ! command -v terraform &> /dev/null; then
    echo "❌ Error: Terraform is not installed. Please install it first."
    exit 1
fi

# Fetch S3 Bucket and CloudFront Distribution ID from Terraform outputs
echo "🔍 Fetching bucket and distribution details from Terraform..."
BUCKET_NAME=$(terraform output -raw s3_bucket_name 2>/dev/null || true)
CF_DIST_ID=$(terraform output -raw cloudfront_distribution_id 2>/dev/null || true)
CUSTOM_DOMAIN=$(terraform output -raw custom_domain_name 2>/dev/null || true)
CF_DOMAIN=$(terraform output -raw cloudfront_domain_name 2>/dev/null || true)

if [ -z "$BUCKET_NAME" ] || [ -z "$CF_DIST_ID" ]; then
    echo "❌ Error: Could not retrieve S3 bucket name or CloudFront Distribution ID from Terraform output."
    echo "   Ensure you have ran 'terraform init' and 'terraform apply' successfully first."
    exit 1
fi

# Determine target web URL origin
if [ -n "$CUSTOM_DOMAIN" ] && [ "$CUSTOM_DOMAIN" != "null" ]; then
    APP_URL="https://$CUSTOM_DOMAIN"
else
    APP_URL="https://$CF_DOMAIN"
fi

echo "📦 Target S3 Bucket: $BUCKET_NAME"
echo "🌐 CloudFront Dist:  $CF_DIST_ID"
echo "🌎 App URL Origin:   $APP_URL"


# ------------------------------------------------------------------------------
# 2. Build Frontend (roadrunner-view)
# ------------------------------------------------------------------------------

FRONTEND_DIR="$SCRIPT_DIR/../../apps/roadrunner-view"

echo "📂 Navigating to frontend directory: $FRONTEND_DIR"
cd "$FRONTEND_DIR"

# Ensure dependencies are installed
if [ ! -d "node_modules" ]; then
    echo "📦 node_modules not found. Installing npm dependencies..."
    npm install
else
    echo "✨ npm dependencies already installed. Skipping install."
fi

# Compile the application
echo "🛠️ Compiling roadrunner-view bundle with target configurations..."
export REACT_APP_COGNITO_REDIRECT_SIGN_IN="$APP_URL/"
export REACT_APP_COGNITO_REDIRECT_SIGN_OUT="$APP_URL/"
export REACT_APP_COGNITO_REDIRECT_URI="$APP_URL"
export REACT_APP_ROADRUNNER_REST_URL_BASE="https://roadrunner.tarterware.com"

npm run build


# ------------------------------------------------------------------------------
# 3. Upload to S3
# ------------------------------------------------------------------------------

echo "📤 Syncing build files to S3 bucket: s3://$BUCKET_NAME..."
aws s3 sync build/ "s3://$BUCKET_NAME" --delete

# ------------------------------------------------------------------------------
# 4. Invalidate CloudFront CDN Cache
# ------------------------------------------------------------------------------

echo "🧹 Invalidating CloudFront cache to serve the new version immediately..."
INVALIDATION_ID=$(aws cloudfront create-invalidation \
    --distribution-id "$CF_DIST_ID" \
    --paths "/*" \
    --query 'Invalidation.Id' \
    --output text)

echo "✅ Invalidation successfully created: $INVALIDATION_ID"
echo "=================================================="
echo "🎉 Deployment Completed Successfully!"
echo "=================================================="
