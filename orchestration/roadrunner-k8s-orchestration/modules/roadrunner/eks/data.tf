data "aws_vpc" "eks_vpc" {
  filter {
    name   = "tag:Name"
    values = [var.eks_vpc_name] # Looks up the VPC by its Name tag, ensuring the correct VPC is used
  }
}

data "aws_security_group" "efs_sg" {
  filter {
    name   = "tag:Name"
    values = [var.efs_sg_name] # Retrieves the security group for EFS by its Name tag
  }
}

data "aws_subnets" "eks_subnets" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.eks_vpc.id] # Finds all subnets associated with the specified VPC
  }
}

