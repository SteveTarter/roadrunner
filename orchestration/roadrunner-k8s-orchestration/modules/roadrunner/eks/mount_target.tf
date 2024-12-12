data "aws_subnet" "subnet_a" {
  filter {
    name   = "availabilityZone"
    values = ["${var.region}a"] 
  }

  filter {
    name   = "tag:Name"
    values = ["${var.eks_vpc_name}-private-${var.region}a"]
  }

  vpc_id = data.aws_vpc.eks_vpc.id
}

data "aws_subnet" "subnet_b" {
  filter {
    name   = "availabilityZone"
    values = ["${var.region}b"] 
  }

  filter {
    name   = "tag:Name"
    values = ["${var.eks_vpc_name}-private-${var.region}b"]
  }

  vpc_id = data.aws_vpc.eks_vpc.id
}

data "aws_subnet" "subnet_c" {
  filter {
    name   = "availabilityZone"
    values = ["${var.region}c"] 
  }

  filter {
    name   = "tag:Name"
    values = ["${var.eks_vpc_name}-private-${var.region}c"]
  }

  vpc_id = data.aws_vpc.eks_vpc.id
}

locals {
  subnets_map = tomap({
    subnet_a = data.aws_subnet.subnet_a.id
    subnet_b = data.aws_subnet.subnet_b.id
    subnet_c = data.aws_subnet.subnet_c.id
  })
}

resource "aws_efs_mount_target" "roadrunner_files" {
  for_each = local.subnets_map

  file_system_id = aws_efs_file_system.roadrunner_files.id
  subnet_id      = each.value
  security_groups = [data.aws_security_group.efs_sg.id]
}

