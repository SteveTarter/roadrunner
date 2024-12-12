terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.75.0"
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
  kubernetes {
    config_path = "~/.kube/config" # Path to the kubeconfig file for Minikube
    config_context = var.cluster_name
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config" # Path to the kubeconfig file for Minikube
  config_context = var.cluster_name
}

provider "kubectl" {
  config_path = "~/.kube/config"
  config_context = var.cluster_name
}

# Define the namespace to be used for Roadrunner
resource "kubernetes_namespace" "roadrunner_namespace" {
  metadata {
    name = var.roadrunner_namespace
  }
}

# Define the secret used to retrieve images from Dockerhub
resource "kubernetes_secret" "dockerhub_secret" {
  metadata {
    name = "dockerhub-secret"
    namespace = var.roadrunner_namespace
  }

  type = "kubernetes.io/dockerconfigjson"

  data = {
    ".dockerconfigjson" = "${data.template_file.docker_config_script.rendered}"
  }
}

data "template_file" "docker_config_script" {
  template = "${file("${path.module}/docker_config.json")}"
  vars = {
    docker-username           = "${var.docker_username}"
    docker-password           = "${var.docker_password}"
    docker-server             = "${var.docker_server}"
    docker-email              = "${var.docker_email}"
    auth                      = base64encode("${var.docker_username}:${var.docker_password}")
  }
}

module "roadrunner" {
  source = "./modules/roadrunner"

  region                         = var.region
  cluster_name                   = var.cluster_name
  roadrunner_namespace           = var.roadrunner_namespace
  mapbox_api_key                 = var.mapbox_api_key
  tarterware_data_dir            = var.tarterware_data_dir
  spring_mail_username           = var.spring_mail_username
  spring_mail_password           = var.spring_mail_password
  auth0_api_client_id            = var.auth0_api_client_id
  auth0_api_client_secret        = var.auth0_api_client_secret
  auth0_api_scope                = var.auth0_api_scope
  auth0_api_issuer_url           = var.auth0_api_issuer_url
  auth0_api_audience             = var.auth0_api_audience
  auth0_api_rest_url_base        = var.auth0_api_rest_url_base
  tarterware_cert_arn            = var.tarterware_cert_arn
  eks_vpc_name                   = var.eks_vpc_name
  efs_sg_name                    = var.efs_sg_name
}

module "roadrunner_view" {
  source = "./modules/roadrunner_view"

  roadrunner_namespace                 = var.roadrunner_namespace
  roadrunner_rest_url_base             = var.roadrunner_rest_url_base
  roadrunner_view_url_base             = var.roadrunner_view_url_base
  mapbox_api_key                       = var.mapbox_api_key
  auth0_api_issuer_url                 = var.auth0_api_issuer_url
  auth0_api_domain                     = var.auth0_api_domain
  roadrunner_view_auth0_client_id      = var.roadrunner_view_auth0_client_id
  roadrunner_view_auth0_client_secret  = var.roadrunner_view_auth0_client_secret
  tarterware_cert_arn                  = var.tarterware_cert_arn
  tarterware_api_audience              = var.tarterware_api_audience

  depends_on = [module.roadrunner]
}

