data "aws_subnet" "subnet_a" {
  filter {
    name   = "availabilityZone"
    values = ["${var.region}a"] # Filters the subnet in availability zone 'a' of the specified region
  }

  filter {
    name   = "tag:Name"
    values = ["${var.eks_vpc_name}-private-${var.region}a"] # Matches subnets by their specific tag name
  }

  vpc_id = data.aws_vpc.eks_vpc.id # Ensures the subnet belongs to the correct VPC
}

data "aws_subnet" "subnet_b" {
  filter {
    name   = "availabilityZone"
    values = ["${var.region}b"] # Filters the subnet in availability zone 'b' of the specified region
  }

  filter {
    name   = "tag:Name"
    values = ["${var.eks_vpc_name}-private-${var.region}b"] # Matches subnets by their specific tag name
  }

  vpc_id = data.aws_vpc.eks_vpc.id # Ensures the subnet belongs to the correct VPC
}

data "aws_subnet" "subnet_c" {
  filter {
    name   = "availabilityZone"
    values = ["${var.region}c"] # Filters the subnet in availability zone 'c' of the specified region
  }

  filter {
    name   = "tag:Name"
    values = ["${var.eks_vpc_name}-private-${var.region}c"] # Matches subnets by their specific tag name
  }

  vpc_id = data.aws_vpc.eks_vpc.id # Ensures the subnet belongs to the correct VPC
}

locals {
  subnets_map = tomap({
    subnet_a = data.aws_subnet.subnet_a.id
    subnet_b = data.aws_subnet.subnet_b.id
    subnet_c = data.aws_subnet.subnet_c.id
  }) # Maps each subnet to its unique ID for iteration
}

resource "aws_efs_mount_target" "roadrunner_files" {
  for_each = local.subnets_map # Creates a mount target for each subnet in the map

  file_system_id = aws_efs_file_system.roadrunner_files.id # Associates the mount target with the EFS file system
  subnet_id      = each.value # Assigns the mount target to the current subnet
  security_groups = [data.aws_security_group.efs_sg.id] # Applies the appropriate security group to the mount target
}

