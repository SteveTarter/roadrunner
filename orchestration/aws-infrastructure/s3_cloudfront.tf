# Generate a random suffix for the S3 bucket to ensure global uniqueness
resource "random_string" "bucket_suffix" {
  length  = 8
  special = false
  upper   = false
}

# ------------------------------------------------------------------------------
# S3 Bucket for Static Web Hosting
# ------------------------------------------------------------------------------

resource "aws_s3_bucket" "this" {
  bucket        = "roadrunner-view-static-hosting-${random_string.bucket_suffix.result}"
  force_destroy = true

  tags = {
    Name        = "${var.environment}-roadrunner-view-bucket"
    Environment = var.environment
  }
}

resource "aws_s3_bucket_website_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  index_document {
    suffix = "index.html"
  }

  error_document {
    key = "index.html"
  }
}

# Block all public access - CloudFront will access S3 via Origin Access Control (OAC)
resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ------------------------------------------------------------------------------
# CloudFront Origin Access Control (OAC)
# ------------------------------------------------------------------------------

resource "aws_cloudfront_origin_access_control" "this" {
  name                              = "${var.environment}-roadrunner-view-oac"
  description                       = "Origin Access Control for Roadrunner View static website"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ------------------------------------------------------------------------------
# CloudFront Distribution
# ------------------------------------------------------------------------------

resource "aws_cloudfront_distribution" "this" {
  origin {
    domain_name              = aws_s3_bucket.this.bucket_regional_domain_name
    origin_id                = "S3-${aws_s3_bucket.this.bucket}"
    origin_access_control_id = aws_cloudfront_origin_access_control.this.id
  }

  enabled             = true
  is_ipv6_enabled     = true
  default_root_object = "index.html"

  # CNAME alias for custom domain if configured
  aliases = var.custom_domain_name != "" ? [var.custom_domain_name] : []

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-${aws_s3_bucket.this.bucket}"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
  }

  # Custom error responses to handle React Router client-side routing.
  # Requests that would otherwise return 404/403 are redirected to index.html with a 200 OK.
  custom_error_response {
    error_caching_min_ttl = 10
    error_code            = 403
    response_code         = 200
    response_page_path    = "/index.html"
  }

  custom_error_response {
    error_caching_min_ttl = 10
    error_code            = 404
    response_code         = 200
    response_page_path    = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # Viewer certificate configuration (Custom domain ACM or default cloudfront)
  viewer_certificate {
    acm_certificate_arn            = var.custom_domain_name != "" && var.tarterware_cert_arn != "" ? var.tarterware_cert_arn : null
    cloudfront_default_certificate = var.custom_domain_name != "" && var.tarterware_cert_arn != "" ? false : true
    ssl_support_method             = var.custom_domain_name != "" && var.tarterware_cert_arn != "" ? "sni-only" : null
    minimum_protocol_version       = var.custom_domain_name != "" && var.tarterware_cert_arn != "" ? "TLSv1.2_2021" : null
  }

  tags = {
    Name        = "${var.environment}-roadrunner-view-cf"
    Environment = var.environment
  }
}

# ------------------------------------------------------------------------------
# S3 Bucket Policy (Allowing CloudFront OAC Read Access)
# ------------------------------------------------------------------------------

resource "aws_s3_bucket_policy" "this" {
  bucket = aws_s3_bucket.this.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "AllowCloudFrontServicePrincipalReadOnly"
        Effect = "Allow"
        Principal = {
          Service = "cloudfront.amazonaws.com"
        }
        Action   = "s3:GetObject"
        Resource = "${aws_s3_bucket.this.arn}/*"
        Condition = {
          StringEquals = {
            "AWS:SourceArn" = aws_cloudfront_distribution.this.arn
          }
        }
      }
    ]
  })
}

# ------------------------------------------------------------------------------
# Route 53 DNS Configuration
# ------------------------------------------------------------------------------

# Look up the existing Route 53 Hosted Zone
data "aws_route53_zone" "selected" {
  count        = var.route53_zone_name != "" ? 1 : 0
  name         = "${var.route53_zone_name}."
  private_zone = false
}

# Create Route 53 Alias record for custom domain pointing to CloudFront
resource "aws_route53_record" "frontend" {
  count           = var.route53_zone_name != "" && var.custom_domain_name != "" ? 1 : 0
  zone_id         = data.aws_route53_zone.selected[0].zone_id
  name            = var.custom_domain_name
  type            = "A"
  allow_overwrite = true

  alias {
    name                   = aws_cloudfront_distribution.this.domain_name
    zone_id                = aws_cloudfront_distribution.this.hosted_zone_id
    evaluate_target_health = false
  }
}


