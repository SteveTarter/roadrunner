variable "namespace" {
  description  = "The Kubernetes namespace where the Kafka cluster resources will be deployed."
  type         = string
}

variable "cluster_name" {
  description = "Name of the Kafka cluster"
  type        = string
  default     = "roadrunner-kafka"
}

variable "kafka_version" {
  description = "Version of Kafka"
  type        = string
  default     = "4.2.0"
}

variable "replicas" {
  description = "Number of replicas"
  type        = number
  default     = 1
}

variable "storage_type" {
  description = "Storage type (ephemeral, persistent-claim)"
  type        = string
  default     = "ephemeral"
}

variable "storage_size" {
  description = "Storage size"
  type        = string
  default     = "20Gi"
}

variable "storage_class" {
  description = "Storage class used for created PersistentVolumes"
  type        = string
  default     = null
}

variable "storage_size_limit" {
  description = "Storage size limit"
  type        = string
  default     = null
}

variable "operator_dependency" {
  description = "Operator dependency"
  type        = any
}
