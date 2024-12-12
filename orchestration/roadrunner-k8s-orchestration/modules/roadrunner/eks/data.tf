data "aws_vpc" "eks_vpc" {
  filter {
    name   = "tag:Name"
    values = [var.eks_vpc_name]
  }
}

data "aws_security_group" "efs_sg" {
  filter {
    name   = "tag:Name"
    values = [var.efs_sg_name]
  }
}

data "aws_subnets" "eks_subnets" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.eks_vpc.id]
  }
}

