terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.75.0" # Pinning version to ensure compatibility and avoid breaking changes in future versions
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.33.0"
    }
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.14.0" # Use the latest version supported
    }
    helm = {
      source = "hashicorp/helm"
    }
    template = {
      source = "hashicorp/template"
    }
  }
}

# Configure AWS provider
provider "aws" {
  region = var.region
}

# Configure Helm provider
provider "helm" {
  kubernetes = {
    config_path = var.kubeconfig_path # Parameterized path for kubeconfig
    config_context = var.cluster_name
  }
}

provider "kubernetes" {
  config_path = var.kubeconfig_path # Parameterized path for kubeconfig
  config_context = var.cluster_name
}

provider "kubectl" {
  config_path = var.kubeconfig_path # Parameterized path for kubeconfig
  config_context = var.cluster_name
}

# Define the namespace to be used for Roadrunner
resource "kubernetes_namespace" "roadrunner_namespace" {
  # Creates a dedicated namespace for the Roadrunner application to organize resources and enforce separation within the Kubernetes cluster.
  metadata {
    name = var.roadrunner_namespace
  }
}

# This module sets up a Redis instance to share data between Roadrunner instances.
module "redis" {
  source = "./modules/redis"

  roadrunner_namespace           = var.roadrunner_namespace
}

module "prometheus" {
  source = "./modules/prometheus"
}

module "strimzi_operator" {
  source          = "./modules/strimzi-operator"
  namespace       = "strimzi"
  watch_namespace = "roadrunner"
  chart_version   = "0.51.0"
}

module "kafka_cluster" {
  source = "./modules/kafka-cluster"

  providers = {
    kubectl = kubectl
  }

  namespace           = kubernetes_namespace.roadrunner_namespace.metadata[0].name
  cluster_name        = "roadrunner-kafka"
  kafka_version       = "4.2.0"
  replicas            = terraform.workspace == "eks" ? 1 : 1

  storage_type        = var.kafka_storage_type
  storage_size        = var.kafka_storage_size
  storage_class       = var.kafka_storage_class

  operator_dependency = module.strimzi_operator
}

module "kafka_topics" {
  source = "./modules/kafka-topics"

  providers = {
    kubectl = kubectl
  }

  namespace    = kubernetes_namespace.roadrunner_namespace.metadata[0].name
  cluster_name = module.kafka_cluster.name

  topics = {
    "vehicle-position-v1" = {
      topic_name = "vehicle.position.v1"
      partitions = 6
      replicas   = 1
      config = {
        "retention.ms" = "604800000"   # 7 days
      }
    }
  }
}

module "roadrunner" {
  source = "./modules/roadrunner"

  # This module sets up the core infrastructure for the Roadrunner application, including networking, IAM roles, and Kubernetes resources.

  cluster_name                 = var.cluster_name
  roadrunner_namespace         = var.roadrunner_namespace
  mapbox_api_key               = var.mapbox_api_key
  spring_mail_username         = var.spring_mail_username
  spring_mail_password         = var.spring_mail_password
  cognito_authority            = var.cognito_authority
  cognito_client_id            = var.cognito_client_id
  tarterware_cert_arn          = var.tarterware_cert_arn
  redis_host                   = module.redis.redis_host
  redis_password               = module.redis.redis_password
  prometheus_release_name      = module.prometheus.prometheus_release_name
  kafka_bootstrap_servers      = module.kafka_cluster.bootstrap_servers
  kafka_topic_vehicle_position = module.kafka_topics.topic_names["vehicle-position-v1"]
}

module "roadrunner_view" {
  source = "./modules/roadrunner_view"

  # This module configures the frontend for the Roadrunner application and relies on the `roadrunner` module to provide backend services and infrastructure. 
  # The dependency ensures that the backend is fully set up before the frontend configuration is applied.

  roadrunner_namespace         = var.roadrunner_namespace
  roadrunner_rest_url_base     = var.roadrunner_rest_url_base
  roadrunner_view_url_base     = var.roadrunner_view_url_base
  mapbox_api_key               = var.mapbox_api_key
  cognito_authority            = var.cognito_authority
  cognito_client_id            = var.cognito_client_id
  cognito_redirect_uri         = var.cognito_redirect_uri
  cognito_user_pool_id         = var.cognito_user_pool_id
  cognito_user_pool_client_id  = var.cognito_user_pool_client_id
  cognito_domain               = var.cognito_domain
  cognito_redirect_sign_in     = var.cognito_redirect_sign_in
  cognito_redirect_sign_out    = var.cognito_redirect_sign_out
  tarterware_cert_arn          = var.tarterware_cert_arn
  tarterware_api_audience      = var.tarterware_api_audience

  depends_on = [module.roadrunner]
}
