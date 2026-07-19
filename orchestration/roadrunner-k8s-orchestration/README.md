# Roadrunner K8s Orchestration

[![HCL](https://img.shields.io/badge/language-HCL-blueviolet)](https://developer.hashicorp.com/terraform/language)
[![Terraform](https://img.shields.io/badge/Terraform-v1.0%2B-623CE4)](https://www.terraform.io/)
[![Kafka](https://img.shields.io/badge/Kafka-4.2.0-231F20)](https://kafka.apache.org/)

This repository contains the Terraform automation and Kubernetes manifests required to deploy the complete **Roadrunner vehicle simulation suite** onto either a local [Minikube](https://minikube.sigs.k8s.io/) cluster or a production-grade [AWS EKS](https://aws.amazon.com/eks/) cluster.

---

## Table of Contents

- [About the Roadrunner System](#about-the-roadrunner-system)
- [Repository Structure](#repository-structure)
- [Architecture Overview](#architecture-overview)
- [Terraform Module Breakdown](#terraform-module-breakdown)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
  - [Variable Reference](#variable-reference)
  - [Global Secrets (terraform.tfvars)](#global-secrets-terraformtfvars)
  - [EKS Configuration (eks.tfvars)](#eks-configuration-ekstfvars)
  - [Minikube Configuration (minikube.tfvars)](#minikube-configuration-minikubetfvars)
- [TLS Certificates](#tls-certificates)
- [Deployment Workflows](#deployment-workflows)
  - [Step 0: Create Terraform Workspaces](#step-0-create-terraform-workspaces)
  - [Option A: Deploy to Minikube (Local)](#option-a-deploy-to-minikube-local)
  - [Option B: Deploy to AWS EKS (Production)](#option-b-deploy-to-aws-eks-production)
- [Verifying the Deployment](#verifying-the-deployment)
- [Operational Notes](#operational-notes)
  - [Clearing Hung Kafka Topics](#clearing-hung-kafka-topics)
- [Teardown](#teardown)
  - [Minikube Teardown](#minikube-teardown)
  - [AWS EKS Teardown](#aws-eks-teardown)
- [Related Repositories](#related-repositories)

---

## About the Roadrunner System

**Roadrunner** is a portfolio-grade, distributed vehicle simulation system built to demonstrate full-stack, cloud, mapping, and distributed-systems development. It simulates fleets of vehicles traveling between real-world addresses at posted road speeds, driven by live Mapbox routing data.

The Roadrunner system spans three components:

| Component | Description |
|---|---|
| [roadrunner](../../apps/roadrunner) | Spring Boot backend — simulation engine, Kafka producer/consumer, REST API |
| [roadrunner-view](../../apps/roadrunner-view) | React + TypeScript frontend — interactive Mapbox map, driver view, playback |
| **roadrunner-k8s-orchestration** *(this directory)* | Terraform + Helm — deploys both services and all supporting infrastructure |

The backend uses a **Hexagonal (Ports and Adapters) Architecture**, making the messaging and persistence layers pluggable. Vehicle positions are streamed to an Apache Kafka topic (`vehicle.position.v1`) at sub-second intervals, and route geometry is cached in Redis to minimize Mapbox API calls.

---

## Repository Structure

```
roadrunner-k8s-orchestration/
├── main.tf                        # Root Terraform configuration; wires all modules together
├── variables.tf                   # All input variable declarations with descriptions and defaults
├── .terraform.lock.hcl            # Provider version lock file
├── clearTopics.sh                 # Utility script to clear stuck Kafka topic finalizers
├── create-cert-authority.txt      # Step-by-step instructions: create a local CA
├── create-roadrunner-certs.txt    # Step-by-step instructions: issue certs signed by that CA
├── redis.helm.out                 # Reference output from the Bitnami Redis Helm chart
├── Resources/img/                 # Images used in documentation
└── modules/
    ├── redis/                     # Bitnami Redis cluster via Helm
    ├── prometheus/                # Prometheus monitoring stack
    ├── strimzi-operator/          # Strimzi Kafka Operator (manages Kafka lifecycle)
    ├── kafka-cluster/             # Kafka cluster resources (KafkaNodePool, Kafka CRDs)
    ├── kafka-topics/              # KafkaTopic CRDs (e.g. vehicle.position.v1)
    ├── roadrunner/                # Roadrunner backend: Deployment, Service, Ingress, IAM
    └── roadrunner_view/           # Roadrunner frontend: Deployment, Service, Ingress
```

---

## Architecture Overview

The orchestration deploys a **Lambda-style streaming architecture** with a caching layer for low-latency route geometry access.

```
┌─────────────────────────────────────────────────────────┐
│                  Kubernetes Cluster                      │
│                                                         │
│  ┌──────────────────┐    vehicle.position.v1 topic      │
│  │  Roadrunner      │──────────────────────────────┐    │
│  │  (Spring Boot)   │                              ▼    │
│  │  REST API        │              ┌───────────────────┐ │
│  │  Simulation Loop │◄────────────►│  Apache Kafka     │ │
│  └──────────────────┘              │  (Strimzi Operator│ │
│           │                        │   v0.51.0)        │ │
│           │ route geometry cache   └───────────────────┘ │
│           ▼                                              │
│  ┌──────────────────┐                                   │
│  │  Redis Cluster   │                                   │
│  │  (Bitnami Helm)  │  ◄── TripPlan & Mapbox            │
│  └──────────────────┘      Directions cache             │
│                                                         │
│  ┌──────────────────┐                                   │
│  │  Roadrunner View │── Reads vehicle states ──►        │
│  │  (React/Nginx)   │   via REST API                    │
│  └──────────────────┘                                   │
│                                                         │
│  ┌──────────────────┐                                   │
│  │  Prometheus      │── Scrapes metrics from ──►        │
│  └──────────────────┘   both services                   │
│                                                         │
│  Ingress: ALB (EKS) or NGINX (Minikube) with TLS        │
└─────────────────────────────────────────────────────────┘
         │                        │
         ▼                        ▼
  roadrunner.<domain>    roadrunner-view.<domain>
```

### Messaging & Telemetry — Apache Kafka

The [Strimzi Operator](https://strimzi.io/) (chart v0.51.0, Kafka 4.2.0) manages the full Kafka cluster lifecycle. The operator is installed in the `strimzi` namespace and configured to watch the `roadrunner` namespace.

The Kafka cluster is named `roadrunner-kafka` and provisions a single `KafkaNodePool` (1 replica for both Minikube and EKS by default, adjustable in `variables.tf`). One topic is created at startup:

| Topic | Partitions | Retention | Purpose |
|---|---|---|---|
| `vehicle.position.v1` | 10 | 7 days (604,800,000 ms) | Real-time vehicle position and lifecycle events |

### Route Caching — Redis

A [Bitnami Redis](https://github.com/bitnami/charts/tree/main/bitnami/redis) cluster is deployed via Helm into the `roadrunner` namespace. It acts as a high-speed cache for:

- **TripPlan objects** — the full list of route legs between stops.
- **Mapbox Directions responses** — raw routing geometry and posted speed limits.

Caching these objects avoids redundant Mapbox API calls during simulation updates and keeps route-segment lookup latency in the single-digit millisecond range.

### Ingress & TLS

- **AWS EKS** — An AWS Application Load Balancer (ALB) is provisioned via the [AWS Load Balancer Controller](https://kubernetes-sigs.github.io/aws-load-balancer-controller/). TLS is terminated at the ALB using an ACM wildcard certificate (see `tarterware_cert_arn`).
- **Minikube** — NGINX Ingress is used, with a self-signed certificate authority and per-service certificates (see [TLS Certificates](#tls-certificates)).

### Monitoring — Prometheus

A Prometheus stack is deployed by the `prometheus` module and is automatically wired to scrape metrics from both the Roadrunner backend and the frontend Nginx pods.

### Authentication — Amazon Cognito

Both the backend REST API and the frontend viewer use **Amazon Cognito** for OIDC-compliant authentication. The backend validates JWT tokens issued by Cognito on every protected endpoint. The frontend uses the Cognito Hosted UI for sign-in/sign-out flows.

---

## Terraform Module Breakdown

The root `main.tf` composes the following modules in dependency order:

| Module | Source | Purpose |
|---|---|---|
| `redis` | `./modules/redis` | Bitnami Redis Helm release |
| `prometheus` | `./modules/prometheus` | Prometheus monitoring stack |
| `strimzi_operator` | `./modules/strimzi-operator` | Installs the Strimzi Kafka Operator |
| `kafka_cluster` | `./modules/kafka-cluster` | Provisions the `roadrunner-kafka` KafkaNodePool and Kafka CRD |
| `kafka_topics` | `./modules/kafka-topics` | Creates the `vehicle.position.v1` KafkaTopic CRD |
| `roadrunner` | `./modules/roadrunner` | Roadrunner backend: Deployment, Service, Ingress, IRSA/IAM, ConfigMaps, Secrets |
| `roadrunner_view` | `./modules/roadrunner_view` | Roadrunner frontend: Deployment, Service, Ingress, ConfigMap (depends on `roadrunner`) |

The providers in use are:

| Provider | Version | Purpose |
|---|---|---|
| `hashicorp/aws` | `~> 5.75.0` | AWS resources (ALB, ACM, IAM) |
| `hashicorp/kubernetes` | `~> 2.33.0` | Kubernetes resources |
| `gavinbunney/kubectl` | `~> 1.14.0` | Raw `kubectl apply` for Strimzi CRDs |
| `hashicorp/helm` | latest | Helm releases (Redis, Prometheus, Strimzi) |
| `hashicorp/template` | latest | Template rendering |

---

## Prerequisites

Before deploying, ensure the following are in place:

1. **Cluster Infrastructure**
   - *EKS*: An existing cluster created with the [eks-with-efs-and-alb](https://github.com/SteveTarter/eks-with-efs-and-alb) Terraform project, which provisions the cluster, EFS storage class, and the AWS Load Balancer Controller.
   - *Minikube*: A running local Minikube instance with the Ingress addon enabled (`minikube addons enable ingress`).

2. **Local CLI Tools**

   | Tool | Minimum Version | Install |
   |---|---|---|
   | `terraform` | 1.0+ | [hashicorp.com/terraform](https://developer.hashicorp.com/terraform/install) |
   | `kubectl` | matches cluster | [kubernetes.io](https://kubernetes.io/docs/tasks/tools/) |
   | `helm` | 3.x | [helm.sh](https://helm.sh/docs/intro/install/) |
   | `aws` CLI | 2.x *(EKS only)* | [aws.amazon.com/cli](https://aws.amazon.com/cli/) |
   | `minikube` | latest *(local only)* | [minikube.sigs.k8s.io](https://minikube.sigs.k8s.io/docs/start/) |

3. **External Service Accounts**
   - **Mapbox** — A valid API token. Sign up at [account.mapbox.com](https://account.mapbox.com/auth/signup/).
   - **Amazon Cognito** — A configured User Pool with a client application and hosted UI. Needed for authentication in both backend and frontend.
   - **AWS** — An IAM user or role with permissions to manage EKS, ALB, ACM, and IAM (EKS deployments only).
   - **SMTP credentials** — Optional; used by Spring Mail for email notifications.

4. **Docker Images**
   Both application images must be available in a container registry accessible to the cluster. The image tags are controlled by `roadrunner_version` and `roadrunner_view_version` variables.

---

## Configuration

### Variable Reference

All variables are declared in `variables.tf`. Sensitive variables are marked `sensitive = true` and must not be committed to source control. Place them in `terraform.tfvars` (which is `.gitignore`d).

| Variable | Required | Default | Description |
|---|---|---|---|
| `cluster_name` | Yes | — | EKS cluster name or `"minikube"` for local |
| `region` | No | `us-east-1` | AWS region (EKS only) |
| `roadrunner_version` | No | `latest` | Docker image tag for the Roadrunner backend |
| `roadrunner_view_version` | No | `latest` | Docker image tag for the Roadrunner View frontend |
| `kubeconfig_path` | No | `~/.kube/config` | Path to your kubeconfig |
| `roadrunner_namespace` | No | `roadrunner` | Kubernetes namespace for all app resources |
| `allowed_cors_origins` | Yes | — | CORS origin(s) allowed by the backend REST API |
| `roadrunner_rest_url_base` | Yes | — | Public base URL of the backend REST API (e.g. `https://roadrunner.example.com`) |
| `roadrunner_view_url_base` | Yes | — | Public base URL of the frontend viewer (e.g. `https://roadrunner-view.example.com`) |
| `mapbox_api_key` | Yes | — | Mapbox API key for routing and tile services |
| `spring_mail_username` | Yes | — | SMTP username for Spring Mail |
| `spring_mail_password` | Yes | — | SMTP password for Spring Mail |
| `roadrunner_user_pool_arn` | Yes | — | ARN of the Cognito User Pool (backend JWT validation) |
| `aws_access_key_id` | Yes | — | AWS access key ID (passed to the backend pod) |
| `aws_secret_access_key` | Yes | — | AWS secret access key (passed to the backend pod) |
| `eks_oidc_provider_arn` | Yes (EKS) | — | OIDC provider ARN for IRSA (IAM Roles for Service Accounts) |
| `cognito_redirect_sign_in` | Yes | — | Cognito Hosted UI redirect URL after login |
| `cognito_redirect_sign_out` | Yes | — | Cognito Hosted UI redirect URL after logout |
| `cognito_authority` | Yes | — | Cognito authority URL (e.g. `https://cognito-idp.<region>.amazonaws.com/<pool-id>`) |
| `cognito_client_id` | Yes | — | Cognito application client ID |
| `cognito_redirect_uri` | Yes | — | Cognito OAuth2 redirect URI |
| `cognito_user_pool_id` | Yes | — | Cognito User Pool ID |
| `cognito_user_pool_client_id` | Yes | — | Cognito User Pool client ID |
| `cognito_domain` | Yes | — | Cognito domain (e.g. `my-app.auth.us-east-1.amazoncognito.com`) |
| `tarterware_cert_arn` | Yes (EKS) | — | ACM certificate ARN for ALB TLS (leave blank for Minikube) |
| `tarterware_api_audience` | Yes | — | API audience value for JWT validation |
| `kafka_storage_type` | No | `ephemeral` | Kafka storage type: `ephemeral` or `persistent-claim` |
| `kafka_storage_size` | No | `20Gi` | Kafka storage volume size |
| `kafka_storage_class` | No | `null` | Kubernetes StorageClass for Kafka PVCs (null = cluster default) |

### Global Secrets (`terraform.tfvars`)

Create this file in the repository root. It is already listed in `.gitignore`.

```hcl
# terraform.tfvars  — DO NOT COMMIT
mapbox_api_key                = "pk.ey..."
spring_mail_username          = "smtp-user@example.com"
spring_mail_password          = "smtp-password"
cognito_authority             = "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX"
cognito_client_id             = "abc123clientid"
cognito_redirect_uri          = "https://roadrunner-view.example.com/"
cognito_user_pool_id          = "us-east-1_XXXXXXXXX"
cognito_user_pool_client_id   = "abc123clientid"
cognito_domain                = "my-app.auth.us-east-1.amazoncognito.com"
tarterware_api_audience       = "https://roadrunner.example.com"
roadrunner_user_pool_arn      = "arn:aws:cognito-idp:us-east-1:123456789012:userpool/us-east-1_XXXXXXXXX"
aws_access_key_id             = "AKIAIOSFODNN7EXAMPLE"
aws_secret_access_key         = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
eks_oidc_provider_arn         = "arn:aws:iam::123456789012:oidc-provider/..."
allowed_cors_origins          = "https://roadrunner-view.example.com"
```

### EKS Configuration (`eks.tfvars`)

This file contains the EKS-specific, non-secret settings.

```hcl
# eks.tfvars
cluster_name               = "<arn-or-name-of-your-eks-cluster>"
region                     = "us-east-1"
roadrunner_namespace       = "roadrunner"
roadrunner_rest_url_base   = "https://roadrunner.example.com"
roadrunner_view_url_base   = "https://roadrunner-view.example.com"
tarterware_cert_arn        = "arn:aws:acm:us-east-1:123456789012:certificate/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
cognito_redirect_sign_in   = "https://roadrunner-view.example.com/"
cognito_redirect_sign_out  = "https://roadrunner-view.example.com/"
kafka_storage_type         = "ephemeral"
kafka_storage_size         = "20Gi"
kafka_storage_class        = null
```

> **Note:** For production use, consider changing `kafka_storage_type` to `persistent-claim` and setting `kafka_storage_class` to an appropriate StorageClass (e.g. the EFS storage class provisioned by `eks-with-efs-and-alb`) to survive pod restarts.

### Minikube Configuration (`minikube.tfvars`)

Uses `.info` TLDs resolved via `/etc/hosts`.

```hcl
# minikube.tfvars
cluster_name               = "minikube"
roadrunner_namespace       = "roadrunner"
roadrunner_rest_url_base   = "https://roadrunner.tarterware.info"
roadrunner_view_url_base   = "https://roadrunner-view.tarterware.info"
tarterware_cert_arn        = ""
cognito_redirect_sign_in   = "https://roadrunner-view.tarterware.info/"
cognito_redirect_sign_out  = "https://roadrunner-view.tarterware.info/"
kafka_storage_type         = "ephemeral"
kafka_storage_size         = "20Gi"
kafka_storage_class        = null
```

---

## TLS Certificates

### Minikube — Self-Signed Certificates

Minikube requires a local Certificate Authority (CA) and per-service signed certificates. Detailed instructions are provided in two files at the root of the repo:

- **`create-cert-authority.txt`** — Creates a local CA (root and intermediate certificates).
- **`create-roadrunner-certs.txt`** — Issues server certificates for `roadrunner.tarterware.info` and `roadrunner-view.tarterware.info`, signed by the CA above.

> **Platform Note:** These scripts were developed on Ubuntu. Windows users should run them inside an Ubuntu environment (WSL2 or a Linux VM).

After generating the CA, you must **import the root CA certificate into your browser's trust store** so that HTTPS connections to the `.info` domains validate correctly.

### EKS — AWS Certificate Manager (ACM)

For EKS, request a **wildcard certificate** in ACM that covers all application subdomains, e.g. `*.example.com`. After it is issued and validated, set `tarterware_cert_arn` in `eks.tfvars` to the certificate's ARN.

---

## Deployment Workflows

### Step 0: Create Terraform Workspaces

Terraform workspaces are used to keep the EKS and Minikube state files separate. Create them once:

```bash
terraform workspace new eks
terraform workspace new minikube
```

### Option A: Deploy to Minikube (Local)

#### 1. Configure `/etc/hosts`

Map the `.info` hostnames to your Minikube IP:

```bash
minikube ip
# 192.168.39.71  (your IP will differ)
```

Add the following line to `/etc/hosts` (requires `sudo`):

```
192.168.39.71    roadrunner.tarterware.info roadrunner-view.tarterware.info
```

#### 2. Enable the Minikube Ingress Addon

```bash
minikube addons enable ingress
```

#### 3. Select the Minikube Workspace and Kubectl Context

```bash
terraform workspace select minikube
kubectl config use-context minikube
```

#### 4. Deploy

```bash
terraform init -upgrade
terraform plan  -var-file=minikube.tfvars
terraform apply -var-file=minikube.tfvars
```

Terraform will:
1. Create the `roadrunner` Kubernetes namespace.
2. Install the Redis Helm release.
3. Install the Prometheus Helm release.
4. Install the Strimzi Kafka Operator in the `strimzi` namespace.
5. Provision the `roadrunner-kafka` Kafka cluster and `vehicle.position.v1` topic.
6. Deploy the Roadrunner backend (Deployment, Service, Ingress, Secrets).
7. Deploy the Roadrunner View frontend (Deployment, Service, Ingress, ConfigMap).

The deployment of the Kafka cluster may take 2–3 minutes as the Strimzi operator bootstraps the broker.

### Option B: Deploy to AWS EKS (Production)

#### 1. Authenticate with AWS

```bash
aws configure   # or set AWS_PROFILE / environment variables
aws eks update-kubeconfig --region us-east-1 --name <cluster-name>
```

#### 2. Select the EKS Workspace and Kubectl Context

```bash
terraform workspace select eks
kubectl config use-context <arn-of-cluster>
```

#### 3. Deploy

```bash
terraform init -upgrade
terraform plan  -var-file=eks.tfvars
terraform apply -var-file=eks.tfvars
```

> The ALB DNS name for the two ingresses can take 3–5 minutes to become active after `apply` completes. Until the ALB is healthy, the application endpoints will not be reachable.

---

## Verifying the Deployment

After `terraform apply` completes, confirm the key Kubernetes resources are healthy:

```bash
# Check Kafka cluster readiness (READY=True is the target)
kubectl get kafka -n roadrunner

# Check the KafkaNodePool
kubectl get kafkanodepool -n roadrunner

# Check all pods are Running/Completed
kubectl get pods -n roadrunner

# Confirm the Kafka bootstrap service exists
kubectl get svc -n roadrunner | grep kafka

# Check the KafkaTopic was created
kubectl get kafkatopic -n roadrunner

# Check Ingress / ALB addresses
kubectl get ingress -n roadrunner
```

**What you want to see:**

- Kafka resource reports `READY: True`
- A `roadrunner-kafka-broker-*` pod is `Running`
- A `roadrunner-kafka-<name>-bootstrap` service exists
- Both `roadrunner` and `roadrunner-view` Ingress resources have an assigned address

---

## Operational Notes

### Clearing Hung Kafka Topics

During a `terraform destroy`, the `vehicle.position.v1` KafkaTopic CRD can sometimes become stuck in a `Terminating` state because Strimzi's finalizer is not removed cleanly. If this happens, run:

```bash
./clearTopics.sh
```

This script patches the KafkaTopic resource to remove its finalizers, unblocking the deletion:

```bash
kubectl patch kafkatopic vehicle-position-v1 -n roadrunner \
  --type=merge -p '{"metadata":{"finalizers":[]}}'
```

### Kafka Storage

By default, both Minikube and EKS deployments use `ephemeral` Kafka storage. This means Kafka topic data is stored on the broker pod's ephemeral disk and is **lost on pod restart**. For EKS environments where message history must survive restarts, set:

```hcl
kafka_storage_type  = "persistent-claim"
kafka_storage_class = "<your-storage-class>"   # e.g. "efs-sc"
kafka_storage_size  = "20Gi"
```

The `vehicle.position.v1` topic has a 7-day retention policy, so with persistent storage up to 7 days of position history is preserved for the viewer's playback feature.

### Prometheus and Metrics

The `prometheus` module deploys a Prometheus instance into the cluster. The Roadrunner backend exposes a `/actuator/prometheus` endpoint (Spring Boot Actuator), which Prometheus scrapes via a configured ServiceMonitor. A Kubernetes secret (`prometheus-token-secret` in the `roadrunner` namespace) is used for Bearer token authentication.

---

## Teardown

### Minikube Teardown

```bash
terraform workspace select minikube
kubectl config use-context minikube

# Destroy all resources
terraform destroy -var-file=minikube.tfvars
```

If Kafka topics are stuck (see [Clearing Hung Kafka Topics](#clearing-hung-kafka-topics)), run `./clearTopics.sh` and then re-run `terraform destroy`.

Verify that everything was removed:

```bash
kubectl get kafkatopic       -n roadrunner    # should return "No resources found"
kubectl get kafka            -n roadrunner    # should return "No resources found"
kubectl get kafkanodepool    -n roadrunner    # should return "No resources found"
kubectl get pods             -n roadrunner    # should return "No resources found"
```

### AWS EKS Teardown

```bash
terraform workspace select eks
kubectl config use-context <arn-of-cluster>

# Destroy all resources
terraform destroy -var-file=eks.tfvars
```

Verify teardown:

```bash
kubectl get kafkatopic       -n roadrunner
kubectl get kafka            -n roadrunner
kubectl get kafkanodepool    -n roadrunner
kubectl get pods             -n roadrunner
```

> **Note:** The ALB provisioned for Ingress is managed by the AWS Load Balancer Controller, not directly by Terraform. Verify in the AWS Console that the load balancer has been deleted to avoid unexpected charges.

---

## Related Components

| Component | Description |
|---|---|
| [roadrunner](../../apps/roadrunner) | Spring Boot simulation backend — Hexagonal Architecture, Kafka producer, Redis cache client, REST API |
| [roadrunner-view](../../apps/roadrunner-view) | React + TypeScript frontend — Mapbox map, real-time vehicle tracking, driver view, simulation playback |
| [eks-with-efs-and-alb](https://github.com/SteveTarter/eks-with-efs-and-alb) | Terraform project that provisions the underlying EKS cluster, EFS storage class, and ALB Ingress Controller |
