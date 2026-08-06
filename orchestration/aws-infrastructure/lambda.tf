# ------------------------------------------------------------------------------
# Cognito Issuer Construction from User Pool ARN
# ------------------------------------------------------------------------------
locals {
  cognito_region  = split(":", var.cognito_user_pool_arn)[3]
  cognito_pool_id = element(split("/", var.cognito_user_pool_arn), length(split("/", var.cognito_user_pool_arn)) - 1)
  cognito_issuer  = "https://cognito-idp.${local.cognito_region}.amazonaws.com/${local.cognito_pool_id}"
}

# ------------------------------------------------------------------------------
# Lambda Security Group & SG Rules
# ------------------------------------------------------------------------------

resource "aws_security_group" "lambda_sg" {
  name        = "${var.environment}-roadrunner-lambda-sg"
  description = "Security group for Roadrunner database API Lambda function"
  vpc_id      = aws_vpc.this.id

  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name        = "${var.environment}-roadrunner-lambda-sg"
    Environment = var.environment
  }
}

# Authorize Lambda SG to access Aurora PostgreSQL port 5432
resource "aws_security_group_rule" "allow_lambda_to_aurora" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  security_group_id        = aws_security_group.aurora_sg.id
  source_security_group_id = aws_security_group.lambda_sg.id
}

# ------------------------------------------------------------------------------
# IAM Role for Lambda
# ------------------------------------------------------------------------------

resource "aws_iam_role" "lambda_role" {
  name = "${var.environment}-roadrunner-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

# Attach standard AWSLambdaVPCAccessExecutionRole policy to allow VPC ENI operations
resource "aws_iam_role_policy_attachment" "lambda_vpc_access" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# ------------------------------------------------------------------------------
# Lambda Function
# ------------------------------------------------------------------------------

resource "aws_lambda_function" "db_api" {
  filename      = "${path.module}/lambda.zip"
  function_name = "${var.environment}-roadrunner-db-api"
  role          = aws_iam_role.lambda_role.arn
  handler       = "index.handler"
  runtime       = "nodejs20.x"
  timeout       = 30
  memory_size   = 256

  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.lambda_sg.id]
  }

  environment {
    variables = {
      DB_HOST     = aws_rds_cluster.this.endpoint
      DB_PORT     = tostring(aws_rds_cluster.this.port)
      DB_NAME     = aws_rds_cluster.this.database_name
      DB_USER     = aws_rds_cluster.this.master_username
      DB_PASSWORD = local.db_password
    }
  }

  # Trigger redeployment when code changes
  source_code_hash = filebase64sha256("${path.module}/lambda.zip")

  tags = {
    Name        = "${var.environment}-roadrunner-db-api-lambda"
    Environment = var.environment
  }
}

# Allow API Gateway to invoke the Lambda function
resource "aws_lambda_permission" "apigw" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.db_api.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.this.execution_arn}/*/*"
}

# ------------------------------------------------------------------------------
# API Gateway HTTP API Setup
# ------------------------------------------------------------------------------

resource "aws_apigatewayv2_api" "this" {
  name          = "${var.environment}-roadrunner-db-api"
  protocol_type = "HTTP"

  cors_configuration {
    allow_credentials = true
    allow_origins     = ["https://roadrunner-view.tarterware.com"]
    allow_headers     = ["Content-Type", "Authorization", "X-Amz-Date", "X-Api-Key", "X-Amz-Security-Token"]
    allow_methods     = ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
    max_age           = 300
  }
}

resource "aws_apigatewayv2_stage" "this" {
  api_id      = aws_apigatewayv2_api.this.id
  name        = "$default"
  auto_deploy = true
}

# Cognito JWT Authorizer
resource "aws_apigatewayv2_authorizer" "cognito" {
  api_id           = aws_apigatewayv2_api.this.id
  name             = "cognito-jwt-authorizer"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  jwt_configuration {
    audience = [var.cognito_client_id]
    issuer   = local.cognito_issuer
  }
}

# Lambda Proxy Integration
resource "aws_apigatewayv2_integration" "lambda" {
  api_id           = aws_apigatewayv2_api.this.id
  integration_type = "AWS_PROXY"

  connection_type        = "INTERNET"
  description            = "Lambda proxy integration for DB API"
  integration_method     = "POST"
  integration_uri        = aws_lambda_function.db_api.invoke_arn
  payload_format_version = "2.0"
}

# ------------------------------------------------------------------------------
# API Gateway HTTP API Routes
# ------------------------------------------------------------------------------

# Public DB Playback Routes
resource "aws_apigatewayv2_route" "public_state" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/db-playback/state"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_route" "public_get_state" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/db-playback/get-vehicle-state"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

# Public DB Vehicle Routes
resource "aws_apigatewayv2_route" "public_sessions" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/db-vehicle/simulation-sessions"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_route" "public_get_session" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/db-vehicle/get-vehicle-session/{vehicleId}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_route" "public_directions" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "ANY /api/db-vehicle/get-vehicle-directions/{vehicleId}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

# Public Bookmark GET Routes
resource "aws_apigatewayv2_route" "public_bookmarks" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "GET /api/db-bookmarks"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

resource "aws_apigatewayv2_route" "public_single_bookmark" {
  api_id    = aws_apigatewayv2_api.this.id
  route_key = "GET /api/db-bookmarks/{vehicleId}"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

# Authenticated Bookmark Write Routes
resource "aws_apigatewayv2_route" "auth_create_bookmark" {
  api_id             = aws_apigatewayv2_api.this.id
  route_key          = "POST /api/db-bookmarks"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_apigatewayv2_route" "auth_update_bookmark" {
  api_id             = aws_apigatewayv2_api.this.id
  route_key          = "PUT /api/db-bookmarks"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

resource "aws_apigatewayv2_route" "auth_delete_bookmark" {
  api_id             = aws_apigatewayv2_api.this.id
  route_key          = "DELETE /api/db-bookmarks/{vehicleId}"
  target             = "integrations/${aws_apigatewayv2_integration.lambda.id}"
  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.cognito.id
}

# ------------------------------------------------------------------------------
# API Gateway Custom Domain Mappings
# ------------------------------------------------------------------------------

resource "aws_apigatewayv2_domain_name" "this" {
  domain_name = var.backend_domain_name

  domain_name_configuration {
    certificate_arn = var.tarterware_cert_arn
    endpoint_type   = "REGIONAL"
    security_policy = "TLS_1_2"
  }
}

resource "aws_apigatewayv2_api_mapping" "this" {
  api_id      = aws_apigatewayv2_api.this.id
  domain_name = aws_apigatewayv2_domain_name.this.id
  stage       = aws_apigatewayv2_stage.this.id
}

# ------------------------------------------------------------------------------
# Route 53 Alias Record
# ------------------------------------------------------------------------------

resource "aws_route53_record" "backend" {
  count           = var.route53_zone_name != "" && var.backend_domain_name != "" ? 1 : 0
  zone_id         = data.aws_route53_zone.selected[0].zone_id
  name            = var.backend_domain_name
  type            = "A"
  allow_overwrite = true

  alias {
    name                   = aws_apigatewayv2_domain_name.this.domain_name_configuration[0].target_domain_name
    zone_id                = aws_apigatewayv2_domain_name.this.domain_name_configuration[0].hosted_zone_id
    evaluate_target_health = false
  }
}
