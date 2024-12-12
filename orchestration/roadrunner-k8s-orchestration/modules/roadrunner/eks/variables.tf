variable "cluster_name" {
  description = "Cluster name"
  type        = string
}

variable "region" {
  description = "AWS Region"
  type        = string
  default     = "us-east-1"
}

variable "roadrunner_namespace" {
  description = "Namespace for Roadrunner application"
  type        = string
  default     = "roadrunner"
}

variable "eks_vpc_name" {
  description = "EKS cluster VPC name"
  type        = string
}

variable "efs_sg_name" {
  description = "EFS security group name"
  type        = string
}
