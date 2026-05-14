variable "cluster_name" {
  description = "The name of the EKS cluster to be created, used for identification and tagging."
  type        = string
}

variable "region" {
  description = "The AWS region of the EKS."
  type        = string
  default     = "us-east-1"
}

variable "roadrunner_version" {
  description = "The Roadrunner tag version to run."
  type        = string
  default     = "latest"
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

variable "roadrunner_user_pool_arn" {
  description = "The ARN for the Cognito User Pool used by Roadrunner."
  sensitive   = true
  type        = string
}

variable "cognito_user_pool_id" {
  description = "The User Pool ID for the Cognito application."
  sensitive   = true
  type        = string
}

variable "aws_access_key_id" {
  description = "The AWS access key ID."
  sensitive   = true
  type        = string
}

variable "aws_secret_access_key" {
  description = "The AWS secret access key."
  sensitive   = true
  type        = string
}

variable "eks_oidc_provider_arn" {
  description = "The EKS OpenID Connect provider URL."
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

variable "kafka_bootstrap_servers" {
  description = "Kafka bootstrap servers addresses"
  type        = string
  default     = null
}

variable "kafka_topic_vehicle_position" {
  description = "Kafka topic for vehicle position events"
  type        = string
  default     = null
}
