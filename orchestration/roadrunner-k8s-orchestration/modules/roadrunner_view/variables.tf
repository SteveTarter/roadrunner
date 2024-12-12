variable "roadrunner_namespace" {
  description = "Namespace for Roadrunner application"
  type = string
  default = "roadrunner"
}

variable "mapbox_api_key" {
  description = "API key for Mapbox"
  sensitive   = true
  type        = string
}

variable "auth0_api_issuer_url" {
  description = "Auth0 API issuer URL"
  sensitive   = true
  type        = string
}

variable "auth0_api_domain" {
  description = "Auth0 API Domain"
  sensitive   = true
  type        = string
}

variable "tarterware_api_audience" {
  description = "Tarterware API Auth0 Audience"
  sensitive   = true
  type        = string
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

variable "tarterware_cert_arn" {
  description = "tarterware certificate ARN"
  type        = string
}

