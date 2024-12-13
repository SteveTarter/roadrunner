variable "cluster_name" {
  description = "The name of the EKS cluster to be created, used for identification and tagging."
  type        = string
}

variable "region" {
  description = "The AWS region where resources will be deployed."
  type        = string
  default     = "us-east-1"
}

variable "roadrunner_namespace" {
  description = "The Kubernetes namespace where the Roadrunner application resources will be deployed."
  type        = string
  default     = "roadrunner"
}

variable "eks_vpc_name" {
  description = "The name of the VPC where the EKS cluster is deployed."
  type        = string
}

variable "efs_sg_name" {
  description = "The name of the security group associated with the EFS file system."
  type        = string
}

