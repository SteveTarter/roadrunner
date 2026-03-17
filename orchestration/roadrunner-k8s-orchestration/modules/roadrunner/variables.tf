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

variable "cognito_authority" {
  description = "The authority URL for Cognito."
  sensitive   = true
  type        = string
}

variable "cognito_client_id" {
  description = "The application client ID in Cognito."
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

variable "redis_password" {
  description = "The password of the Redis service"
  sensitive   = true
  type        = string
}

variable "prometheus_release_name" {
  description = "The name of the Prometheus release"
  type        = string
}

variable "enable_service_monitor" {
  type        = bool
  description = "Create the ServiceMonitor after the Prometheus CRDs exist"
  default     = false
}
