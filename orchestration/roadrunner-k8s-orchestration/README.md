## Description
This project provides the Terraform automation and Kubernetes manifests required to deploy the Roadrunner simulation suite. The deployment consists of two primary components:
- [Roadrunner Server:](https://github.com/SteveTarter/roadrunner) The core simulation engine and Kafka producer/consumer.
- [Roadrunner View:](https://github.com/SteveTarter/roadrunner-view) The frontend visualization application.

The orchestration supports both local development via **Minikube** and production-grade scaling on **AWS EKS**.

## Architecture Overview
The Roadrunner orchestration deploys a hybrid state management system designed for high-performance simulation. The infrastructure is managed via Terraform and Helm, utilizing a "Speed Layer" for telemetry and a "Cache Layer" for static route data.

### Messaging & Telemetry (Kafka)
- **Strimzi Kafka Operator:** Manages the lifecycle of the messaging backbone.
- **Event-Driven Stream:** Provisions the `vehicle.position.v1` topic to handle real-time position updates and lifecycle events.

### Route Caching (Redis)
- **Bitnami Redis Cluster:** Provisioned via **Helm**, this cluster serves as the primary cache for vehicle route geometry and Mapbox direction data.
- **Performance Optimization:** By caching static TripPlan and Directions data in Redis, the system reduces redundant API calls to Mapbox and ensures low-latency access to route segments during simulation updates.

### Traffic & Ingress
- **Ingress Control:** Configures AWS Application Load Balancers (ALB) or NGINX (Minikube) to provide TLS-secured endpoints for the REST API and the frontend viewer.

## Prerequisites
Before deploying, ensure you have the following:
1. **Cluster Infrastructure:** An existing EKS cluster created via [eks-with-efs-and-alb](https://github.com/SteveTarter/eks-with-efs-and-alb) or a local Minikube instance.
2. **Helm:** Installed locally to support the **Bitnami Redis** and **Strimzi Kafka** deployments.
3. **External Providers:** 
    - Mapbox: A valid API token for route geometry and tile services.
    - Cognito: Credentials for OIDC-compliant authentication.
4. **Local Tools:** `terraform` (v1.0+), `kubectl`, `aws` (if deploying to EKS), and `minikube` (if testing locally).

## Configuration
Preparation involves setting up your variable files. Sensitive data belongs in `terraform.tfvars`, while environment-specific settings are split by workspace.

1. **Global Secrets (`terraform.tfvars`)**
Create this file in the root directory to store credentials:
```terraform
# terraform.tfvars
mapbox_api_key                = ""
spring_mail_username          = ""
spring_mail_password          = ""
cognito_authority             = ""
cognito_client_id             = ""
cognito_redirect_uri          = ""
cognito_user_pool_id          = ""
cognito_user_pool_client_id   = ""
cognito_domain                = ""
tarterware_api_audience       = ""
```

2. **EKS (`eks.tfvars`)**
Focuses on AWS ARNs and production domain names:
```terraform
# eks.tfvars
cluster_name               = "<arn of cluster>"
roadrunner_namespace       = "roadrunner"
roadrunner_rest_url_base   = "https://roadrunner.<my-domain>.com"
roadrunner_view_url_base   = "https://roadrunner-view.<my-domain>.com"
tarterware_cert_arn        = "<arn of certificate>"
cognito_redirect_sign_in   = "https://roadrunner-view.<my-domain>.com/"
cognito_redirect_sign_out  = "https://roadrunner-view.<my-domain>.com/"
kafka_storage_type         = "ephemeral"
kafka_storage_size         = "20Gi"
kafka_storage_class        = null
```

3. **Minikube (`minikube.tfvars`)**
Uses local `.info` TLDs and ephemeral storage for Kafka.
```terraform
# minikube.tfvars
cluster_name               = "minikube"
roadrunner_namespace       = "roadrunner"
roadrunner_rest_url_base   = "https://roadrunner.tarterware.info"
roadrunner_view_url_base   = "https://roadrunner-view.tarterware.info"
tarterware_cert_arn        = ""
cognito_redirect_sign_in   = "https://roadrunner-view.<my-domain>.info/"
cognito_redirect_sign_out  = "https://roadrunner-view.<my-domain>.info/"
kafka_storage_type         = "ephemeral"
kafka_storage_size         = "20Gi"
kafka_storage_class        = null
```

## Deployment Workflows

### Create Terraform Workspaces
Ensure separate Terraform workspaces exist for EKS and Minikube:

```bash
terraform workspace new eks
terraform workspace new minikube
```

## Certificates
Refer to `create-cert-authority.txt` and `create-roadrunner-certs.txt` for generating certificates. These instructions were developed for Ubuntu. Windows users may need to run the scripts in an Ubuntu environment. The certificate authority (CA) must be installed in your browser for proper TLS validation.

For EKS, request a wildcard certificate that supports subdomains (e.g., `*.tarterware.com`).

### Option A: Local Deployment on Minikube
1. Update your `/etc/hosts` file to map domain names to the Minikube IP address. First, get the IP:

```bash
minikube ip
192.168.39.71
```

Add an entry like this to your `/etc/hosts` file:

```
192.168.39.71    roadrunner.tarterware.info roadrunner-view.tarterware.info
```

2. Select the Minikube workspace and context:

```bash
terraform workspace select minikube
kubectl config use-context minikube
```

3. Deploy the application:

```bash
terraform init -upgrade

terraform plan -var-file=minikube.tfvars
terraform apply  -var-file=minikube.tfvars
```

4. Verify the cluster is ready:

```bash
kubectl get kafka -n roadrunner
kubectl get kafkanodepool -n roadrunner
kubectl get pods -n roadrunner
kubectl get svc -n roadrunner | grep kafka
```

What you want to see:
*   Kafka resource shows `READY=True`
*   broker pod exists and is Running
*   bootstrap service exists

## Option B: Deploy to AWS EKS
1. Select the EKS workspace and context:

```bash
terraform workspace select eks
kubectl config use-context <arn-of-cluster>
```

2. Deploy the application.  The service monitor in deployed in stages:

```bash
terraform init -upgrade
terraform plan -var-file=eks.tfvars
terraform apply -var-file=eks.tfvars
```

3. Verify the cluster is ready:

```bash
kubectl get kafka -n roadrunner
kubectl get kafkanodepool -n roadrunner
kubectl get pods -n roadrunner
kubectl get svc -n roadrunner | grep kafka
```

What you want to see:
*   Kafka resource shows `READY=True`
*   broker pod exists and is Running
*   bootstrap service exists

## Teardown

### Minikube Teardown

1. Destroy the application:

```bash
terraform destroy -var-file=minikube.tfvars
```

2. Verify that the topics are gone:

```bash
kubectl get kafkatopic -n roadrunner
```

3. Verify the Kafka resources and pool are gone:

```bash
kubectl get kafka -n roadrunner
kubectl get kafkanodepool -n roadrunner
kubectl get pods -n roadrunner
```

### AWS EKS Teardown

1. Destroy the stack:

```bash
terraform destroy -var-file=eks.tfvars
```

2. Verify that the topics are actually gone:

```bash
kubectl get kafkatopic -n roadrunner
```

3. Verify the Kafka resources and pool are gone:

```bash
kubectl get kafka -n roadrunner
kubectl get kafkanodepool -n roadrunner
kubectl get pods -n roadrunner
```
