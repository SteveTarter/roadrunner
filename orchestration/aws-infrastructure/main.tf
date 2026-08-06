terraform {
  required_version = ">= 1.0.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.75.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# Fetch available Availability Zones
data "aws_availability_zones" "available" {
  state = "available"
}

# ------------------------------------------------------------------------------
# VPC Networking Setup
# ------------------------------------------------------------------------------

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name        = "${var.environment}-roadrunner-vpc"
    Environment = var.environment
  }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name        = "${var.environment}-roadrunner-igw"
    Environment = var.environment
  }
}

# Public Subnets
resource "aws_subnet" "public" {
  count                   = length(var.public_subnet_cidrs)
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name        = "${var.environment}-roadrunner-public-${count.index + 1}"
    Environment = var.environment
  }
}

# Private Subnets
resource "aws_subnet" "private" {
  count             = length(var.private_subnet_cidrs)
  vpc_id            = aws_vpc.this.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name        = "${var.environment}-roadrunner-private-${count.index + 1}"
    Environment = var.environment
  }
}

# Route Table for Public Subnets
resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = {
    Name        = "${var.environment}-roadrunner-public-rt"
    Environment = var.environment
  }
}

resource "aws_route_table_association" "public" {
  count          = length(var.public_subnet_cidrs)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# NAT Gateway for Private Subnets (to allow outbound traffic for API calls like Mapbox)
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name        = "${var.environment}-roadrunner-nat-eip"
    Environment = var.environment
  }
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id # Place NAT Gateway in the first public subnet

  tags = {
    Name        = "${var.environment}-roadrunner-nat-gw"
    Environment = var.environment
  }

  depends_on = [aws_internet_gateway.this]
}

# Route Table for Private Subnets
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this.id
  }

  tags = {
    Name        = "${var.environment}-roadrunner-private-rt"
    Environment = var.environment
  }
}

resource "aws_route_table_association" "private" {
  count          = length(var.private_subnet_cidrs)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ------------------------------------------------------------------------------
# Aurora DB Subnet Group
# ------------------------------------------------------------------------------

resource "aws_db_subnet_group" "this" {
  name        = "${var.environment}-roadrunner-db-subnet-group"
  description = "Subnet group for Aurora Serverless v2 PostgreSQL cluster"
  # Use public subnets if publicly accessible is true to allow resolving to public IPs,
  # otherwise use private subnets.
  subnet_ids = var.db_publicly_accessible ? aws_subnet.public[*].id : aws_subnet.private[*].id

  tags = {
    Name        = "${var.environment}-roadrunner-db-subnet-group"
    Environment = var.environment
  }
}

# ------------------------------------------------------------------------------
# Security Group (Minikube Connectivity)
# ------------------------------------------------------------------------------

resource "aws_security_group" "aurora_sg" {
  name        = "${var.environment}-roadrunner-aurora-sg"
  description = "Security group for Aurora cluster, allowing Minikube and internal connections"
  vpc_id      = aws_vpc.this.id

  # Ingress rule for local developer/Minikube connection
  ingress {
    description = "Allow PostgreSQL traffic from local Minikube developer machine"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.minikube_allowed_cidrs
  }

  # Ingress rule for resources running inside the VPC CIDR
  ingress {
    description = "Allow PostgreSQL traffic from internal VPC CIDR"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  # Egress to allow updates and external communication (standard practice)
  egress {
    from_port        = 0
    to_port          = 0
    protocol         = "-1"
    cidr_blocks      = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  tags = {
    Name        = "${var.environment}-roadrunner-aurora-sg"
    Environment = var.environment
  }
}

# ------------------------------------------------------------------------------
# Password Generation
# ------------------------------------------------------------------------------

resource "random_password" "db_pwd" {
  count   = var.db_password == "" ? 1 : 0
  length  = 24
  special = false # Avoid special characters that can sometimes cause issues in JDBC connection strings
}

locals {
  db_password = var.db_password == "" ? random_password.db_pwd[0].result : var.db_password
}

# ------------------------------------------------------------------------------
# Aurora Serverless v2 PostgreSQL Cluster
# ------------------------------------------------------------------------------

resource "aws_rds_cluster" "this" {
  cluster_identifier = "${var.environment}-roadrunner-aurora"
  engine             = "aurora-postgresql"
  engine_mode        = "provisioned"
  engine_version     = var.db_engine_version # High compatibility with the helm postgres version

  database_name   = var.db_name
  master_username = var.db_username
  master_password = local.db_password

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.aurora_sg.id]

  # Configure Serverless v2 Scaling
  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 2.0
  }

  skip_final_snapshot = true

  tags = {
    Name        = "${var.environment}-roadrunner-aurora-cluster"
    Environment = var.environment
  }
}

resource "aws_rds_cluster_instance" "this" {
  count = 1

  identifier           = "${var.environment}-roadrunner-aurora-instance-1"
  cluster_identifier   = aws_rds_cluster.this.id
  instance_class       = "db.serverless"
  engine               = aws_rds_cluster.this.engine
  engine_version       = aws_rds_cluster.this.engine_version
  publicly_accessible  = var.db_publicly_accessible
  db_subnet_group_name = aws_db_subnet_group.this.name

  tags = {
    Name        = "${var.environment}-roadrunner-aurora-instance-1"
    Environment = var.environment
  }
}
