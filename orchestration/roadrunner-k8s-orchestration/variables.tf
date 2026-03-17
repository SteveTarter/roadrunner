variable "cluster_name" {
  description = "The name of the EKS cluster to be created, used for identification and tagging."
  type        = string
}

variable "kubeconfig_path" {
  description = "The path to the kubeconfig file used for Kubernetes cluster access."
  type        = string
  default     = "~/.kube/config"
}

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

variable "cognito_redirect_sign_in" {
  description = "The redirect sign in URL for the Cognito application."
  sensitive   = true
  type        = string
}

variable "cognito_redirect_sign_out" {
  description = "The redirect sign outin URL for the Cognito application."
  sensitive   = true
  type        = string
}

variable "cognito_authority" {
  description = "The authority URL for Cognito."
  sensitive   = true
  type        = string
}

variable "cognito_client_id" {
  description = "The client ID for the Cognito app"
  sensitive   = true
  type        = string
}

variable "cognito_redirect_uri" {
  description = "The redirect URL for the Cognito application."
  sensitive   = true
  type        = string
}

variable "cognito_user_pool_id" {
  description = "The User Pool ID for the Cognito application."
  sensitive   = true
  type        = string
}

variable "cognito_user_pool_client_id" {
  description = "The User Pool ID for the Cognito application."
  sensitive   = true
  type        = string
}

variable "cognito_domain" {
  description = "The domain for the Cognito application."
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

variable "enable_service_monitor" {
  type        = bool
  description = "Create the ServiceMonitor after the Prometheus CRDs exist"
  default     = false
}
