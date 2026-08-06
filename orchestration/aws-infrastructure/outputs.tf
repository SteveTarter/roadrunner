output "aurora_endpoint" {
  description = "The cluster endpoint for the Aurora Serverless v2 cluster."
  value       = aws_rds_cluster.this.endpoint
}

output "aurora_reader_endpoint" {
  description = "The cluster reader endpoint for the Aurora Serverless v2 cluster."
  value       = aws_rds_cluster.this.reader_endpoint
}

output "aurora_port" {
  description = "The port the database is listening on."
  value       = aws_rds_cluster.this.port
}

output "aurora_db_name" {
  description = "The name of the database created."
  value       = aws_rds_cluster.this.database_name
}

output "aurora_username" {
  description = "The master username for the database."
  value       = aws_rds_cluster.this.master_username
}

output "aurora_password" {
  description = "The master password for the database."
  value       = local.db_password
  sensitive   = true
}

output "s3_bucket_name" {
  description = "The name of the S3 bucket used for hosting the static website."
  value       = aws_s3_bucket.this.bucket
}

output "cloudfront_domain_name" {
  description = "The domain name of the CloudFront distribution."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "cloudfront_distribution_id" {
  description = "The ID of the CloudFront distribution (used for invalidations)."
  value       = aws_cloudfront_distribution.this.id
}

output "custom_domain_name" {
  description = "The custom domain name configured for the frontend."
  value       = var.custom_domain_name
}

