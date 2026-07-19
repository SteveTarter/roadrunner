locals {
  count       = terraform.workspace == "eks" ? 1 : 0
  # Extracts 'tarterware-eks' from 'arn:aws:eks:...:cluster/tarterware-eks'
  cluster_short_name = element(split("/", var.cluster_name), length(split("/", var.cluster_name)) - 1)
}

# IAM Policy for Cognito access (EKS only)
resource "aws_iam_policy" "cognito_access" {
  count       = terraform.workspace == "eks" ? 1 : 0
  name        = "${local.cluster_short_name}-cognito-access"
  description = "Allows Roadrunner to list users in Cognito for email lookups"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "cognito-idp:ListUsers"
        Resource = var.roadrunner_user_pool_arn
      }
    ]
  })
}

# IAM Role for Service Accounts (IRSA) (EKS only)
module "iam_eks_role" {
  count   = terraform.workspace == "eks" ? 1 : 0
  source  = "terraform-aws-modules/iam/aws//modules/iam-role-for-service-accounts-eks"
  version = "~> 5.30"

  role_name = "${local.cluster_short_name}-roadrunner-irsa"

  role_policy_arns = {
    policy = aws_iam_policy.cognito_access[0].arn
  }

  oidc_providers = {
    main = {
      provider_arn               = var.eks_oidc_provider_arn
      namespace_service_accounts = ["${var.roadrunner_namespace}:roadrunner-sa"]
    }
  }
}

# Kubernetes Service Account (Annotated with Role ARN for EKS)
resource "kubernetes_service_account" "roadrunner_sa" {
  metadata {
    name      = "roadrunner-sa"
    namespace = var.roadrunner_namespace
    annotations = terraform.workspace == "eks" ? {
      "eks.amazonaws.com/role-arn" = module.iam_eks_role[0].iam_role_arn
    } : {}
  }
}
