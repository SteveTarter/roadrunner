variable "enabled" {
  description = "Create Kafka topis"
  type        = bool
  default     = false
}

variable "namespace" {
  description = "Namespace"
  type        = string
}

variable "cluster_name" {
  description = "Cluster name"
  type        = string
}

variable "topics" {
  description = "Kafka topics map"
  type = map(object({
    topic_name  = optional(string)
    partitions  = number
    replicas    = number
    config      = optional(map(string), {})
  }))
  default = {}
}
