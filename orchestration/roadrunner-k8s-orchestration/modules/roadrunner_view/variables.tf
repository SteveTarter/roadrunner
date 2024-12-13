variable "roadrunner_namespace" {
  description = "The Kubernetes namespace where the Roadrunner application resources will be deployed."
  type        = string
  default     = "roadrunner"
}

variable "roadrunner_rest_url_base" {
  description = "The base URL for the Roadrunner REST API."
  sensitive   = true
  type        = string
}

variable "roadrunner_view_url_base" {
  description = "The public base URL for the Roadrunner View application."
  sensitive   = true
  type        = string
}

variable "mapbox_api_key" {
  description = "The API key used to access Mapbox services."
  sensitive   = true
  type        = string
}

variable "auth0_api_issuer_url" {
  description = "The issuer URL for the Auth0 API, used for token verification."
  sensitive   = true
  type        = string
}

variable "auth0_api_domain" {
  description = "The domain name for the Auth0 API."
  sensitive   = true
  type        = string
}

variable "roadrunner_view_auth0_client_id" {
  description = "The client ID for the Roadrunner View application within Auth0."
  sensitive   = true
  type        = string
}

variable "roadrunner_view_auth0_client_secret" {
  description = "The client secret for the Roadrunner View application within Auth0."
  sensitive   = true
  type        = string
}

variable "tarterware_cert_arn" {
  description = "The ARN of the SSL/TLS certificate for securing communication with Tarterware services."
  type        = string
}

variable "tarterware_api_audience" {
  description = "The Auth0 audience value for the Tarterware API."
  sensitive   = true
  type        = string
}

