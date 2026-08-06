variable "aws_region" {
  description = "The AWS region to deploy resources."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "The deployment environment name (e.g., dev, prod)."
  type        = string
  default     = "dev"
}

variable "vpc_cidr" {
  description = "The CIDR block for the VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (must have at least 2 in different AZs)."
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  description = "CIDR blocks for private subnets (must have at least 2 in different AZs)."
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "db_name" {
  description = "The name of the database to create inside the Aurora cluster."
  type        = string
  default     = "roadrunner_gis"
}

variable "db_username" {
  description = "The database administrator username."
  type        = string
  default     = "roadrunner"
}

variable "db_password" {
  description = "The database administrator password. If empty, a random password will be generated."
  type        = string
  default     = ""
  sensitive   = true
}

variable "db_publicly_accessible" {
  description = "Whether the Aurora cluster instances should be publicly accessible (required for direct Minikube connection)."
  type        = bool
  default     = true
}

variable "minikube_allowed_cidrs" {
  description = "CIDR blocks permitted to connect to the Aurora PostgreSQL port (5432)."
  type        = list(string)
  default     = ["104.0.72.89/32"]
}

variable "tarterware_cert_arn" {
  description = "The ARN of the ACM certificate in us-east-1 for the custom domain roadrunner-view.tarterware.com."
  type        = string
  default     = "arn:aws:acm:us-east-1:755935564186:certificate/6d587c4b-4620-470d-bb0c-73a34ac81752"
}

variable "custom_domain_name" {
  description = "The custom domain name for the frontend static website hosted on CloudFront."
  type        = string
  default     = "roadrunner-view.tarterware.com"
}

variable "route53_zone_name" {
  description = "The Route 53 hosted zone name for the custom domain. If empty, the DNS record will not be created."
  type        = string
  default     = "tarterware.com"
}

variable "db_engine_version" {
  description = "The engine version for the Aurora PostgreSQL cluster."
  type        = string
  default     = "15.18"
}

variable "cognito_user_pool_arn" {
  description = "The ARN of the Cognito User Pool for backend auth."
  type        = string
  default     = "arn:aws:cognito-idp:us-east-1:755935564186:userpool/us-east-1_URgbtAzM8"
}

variable "cognito_client_id" {
  description = "The Cognito User Pool App Client ID."
  type        = string
  default     = "21idckuqlmf51kvkbg5mnt8k9f"
}

variable "backend_domain_name" {
  description = "The custom domain name for the backend REST API."
  type        = string
  default     = "roadrunner.tarterware.com"
}



