terraform {
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.33.0"
    }
    helm = {
      source  = "hashicorp/helm"
      version = "~> 2.16.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6.0"
    }
  }
}

provider "helm" {
  kubernetes {
    config_path    = var.kubeconfig_path
    config_context = var.cluster_name
  }
}

provider "kubernetes" {
  config_path    = var.kubeconfig_path
  config_context = var.cluster_name
}

resource "random_password" "db_pwd" {
  count   = var.db_password == "" ? 1 : 0
  length  = 16
  special = false
}

locals {
  db_password = var.db_password == "" ? random_password.db_pwd[0].result : var.db_password
}

resource "helm_release" "postgis" {
  name       = "postgis"
  namespace  = var.roadrunner_namespace
  chart      = "postgresql"
  repository = "oci://registry-1.docker.io/bitnamicharts"
  version    = "15.5.4" # This is a robust, widely tested version of the Bitnami Postgres chart

  # Use the standard postgres values structure
  set {
    name  = "auth.username"
    value = var.db_username
  }
  set {
    name  = "auth.database"
    value = var.db_name
  }
  set {
    name  = "image.tag"
    value = "latest"
  }
  set {
    name  = "primary.persistence.size"
    value = var.storage_size
  }
  set {
    name  = "primary.persistence.enabled"
    value = "true"
  }

  set_sensitive {
    name  = "auth.password"
    value = local.db_password
  }
  set_sensitive {
    name  = "auth.postgresPassword"
    value = local.db_password
  }

  # Inject the SQL script to enable PostGIS during database initialization
  values = [
    <<-EOT
    primary:
      initdb:
        scripts:
          enable-postgis.sql: |
            \c ${var.db_name} postgres
            CREATE EXTENSION IF NOT EXISTS postgis;
            CREATE EXTENSION IF NOT EXISTS postgis_topology;
    EOT
  ]
}

# Create a Kubernetes Secret containing database credentials for client consumption
resource "kubernetes_secret_v1" "db_secret" {
  metadata {
    name      = "roadrunner-postgis-secret"
    namespace = var.roadrunner_namespace
  }

  data = {
    "db-host"     = "postgis-postgresql.${var.roadrunner_namespace}.svc.cluster.local"
    "db-port"     = "5432"
    "db-name"     = var.db_name
    "db-user"     = var.db_username
    "db-password" = local.db_password
  }
}
