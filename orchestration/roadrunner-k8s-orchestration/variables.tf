variable "cluster_name" {
  description = "Cluster name"
  type        = string
}

variable "region" {
  description = "AWS Region"
  type        = string
  default     = "us-east-1"
}

variable "roadrunner_namespace" {
  description = "Namespace for Roadrunner application"
  type        = string
  default     = "roadrunner"
}

variable "roadrunner_rest_url_base" {
  description = "Roadrunner REST URL Base URL"
  sensitive   = true
  type        = string
}

variable "roadrunner_view_url_base" {
  description = "Roadrunner View URL Base URL"
  sensitive   = true
  type        = string
}

variable "mapbox_api_key" {
  description = "API key for Mapbox"
  sensitive   = true
  type        = string
}

variable "spring_mail_username" {
  description = "Spring Mail username"
  sensitive   = true
  type        = string
}

variable "spring_mail_password" {
  description = "Spring Mail password"
  sensitive   = true
  type        = string
}

variable "auth0_api_audience" {
  description = "Auth0 API audience"
  sensitive   = true
  type        = string
}

variable "auth0_api_client_id" {
  description = "Auth0 API client ID"
  sensitive   = true
  type        = string
}

variable "auth0_api_client_secret" {
  description = "Auth0 API client secret"
  sensitive   = true
  type        = string
}

variable "auth0_api_scope" {
  description = "Auth0 API scope"
  sensitive   = true
  type        = string
}

variable "auth0_api_issuer_url" {
  description = "Auth0 API issuer URL"
  sensitive   = true
  type        = string
}

variable "auth0_api_rest_url_base" {
  description = "Auth0 API REST URL base"
  sensitive   = true
  type        = string
}

variable "auth0_api_domain" {
  description = "Auth0 API Domain"
  sensitive   = true
  type        = string
}

variable "roadrunner_view_auth0_client_id" {
  description = "Roadrunner View App Auth0 client ID"
  sensitive   = true
  type        = string
}

variable "roadrunner_view_auth0_client_secret" {
  description = "Roadrunner View App Auth0 client secret"
  sensitive   = true
  type        = string
}

variable "tarterware_data_dir" {
  description = "Tarterware Data directory"
  sensitive   = true
  type        = string
}

variable "tarterware_cert_arn" {
  description = "tarterware certificate ARN"
  type        = string
}

variable "eks_vpc_name" {
  description = "EKS cluster VPC name"
  type        = string
}

variable "efs_sg_name" {
  description = "EFS security group name"
  type        = string
}

variable "tarterware_api_audience" {
  description = "Tarterware API Auth0 Audience"
  sensitive   = true
  type        = string
}

