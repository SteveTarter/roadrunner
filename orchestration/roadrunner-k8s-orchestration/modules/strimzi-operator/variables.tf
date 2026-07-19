variable "namespace" {
  type    = string
  default = "strimzi"
}

variable "watch_namespace" {
  type = string
}

variable "chart_version" {
  type    = string
  default = "0.51.0"
}
