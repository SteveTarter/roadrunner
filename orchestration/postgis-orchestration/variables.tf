variable "cluster_name" {
  description = "The name of the cluster (e.g., minikube or EKS cluster name)."
  type        = string
  default     = "minikube"
}

variable "kubeconfig_path" {
  description = "The path to the kubeconfig file used for Kubernetes cluster access."
  type        = string
  default     = "~/.kube/config"
}

variable "roadrunner_namespace" {
  description = "The Kubernetes namespace where the database resources will be deployed."
  type        = string
  default     = "roadrunner"
}

variable "db_name" {
  description = "The name of the database to create."
  type        = string
  default     = "roadrunner_gis"
}

variable "db_username" {
  description = "The username for the database."
  type        = string
  default     = "roadrunner"
}

variable "db_password" {
  description = "The password for the database. If empty, a random password will be generated."
  type        = string
  default     = ""
  sensitive   = true
}

variable "storage_size" {
  description = "The size of the persistent volume claim."
  type        = string
  default     = "10Gi"
}

variable "storage_class" {
  description = "The storage class to use for persistence."
  type        = string
  default     = null
}
