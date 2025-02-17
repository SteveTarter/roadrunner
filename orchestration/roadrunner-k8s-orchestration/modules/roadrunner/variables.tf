variable "cluster_name" {
  description = "The name of the EKS cluster to be created, used for identification and tagging."
  type        = string
}

variable "roadrunner_namespace" {
  description = "The Kubernetes namespace where the Roadrunner application resources will be deployed."
  type = string
  default = "roadrunner"
}

variable "mapbox_api_key" {
  description = "The API key used to access Mapbox services."
  type        = string
  sensitive = true
}

variable "spring_mail_username" {
  description = "The username for the Spring Mail service."
  sensitive   = true
  type        = string
}

variable "spring_mail_password" {
  description = "The password for the Spring Mail service."
  sensitive   = true
  type        = string
}

variable "auth0_api_audience" {
  description = "The audience value for the Auth0 API, used for authentication."
  sensitive   = true
  type        = string
}

variable "auth0_api_client_id" {
  description = "The client ID for the Auth0 API, used for authentication."
  sensitive   = true
  type        = string
}

variable "auth0_api_client_secret" {
  description = "The client secret for the Auth0 API, used for secure authentication."
  sensitive   = true
  type        = string
}

variable "auth0_api_scope" {
  description = "The scope for the Auth0 API, defining access permissions."
  sensitive   = true
  type        = string
}

variable "auth0_api_issuer_url" {
  description = "The issuer URL for the Auth0 API, used for token verification."
  sensitive   = true
  type        = string
}

variable "auth0_api_rest_url_base" {
  description = "The base URL for the Auth0 API REST endpoint."
  sensitive   = true
  type        = string
}

variable "tarterware_cert_arn" {
  description = "The ARN of the SSL/TLS certificate for securing communication with Tarterware services."
  type        = string
}

variable "redis_host" {
  description = "The hostname of the Redis service"
  type        = string
}

variable "prometheus_release_name" {
  description = "The name of the Prometheus release"
  type        = string
}

