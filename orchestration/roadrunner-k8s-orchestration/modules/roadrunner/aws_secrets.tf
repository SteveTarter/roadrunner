# modules/roadrunner/aws_secrets.tf

resource "kubernetes_secret" "aws_credentials" {
  count = terraform.workspace == "minikube" ? 1 : 0

  metadata {
    name      = "aws-credentials"
    namespace = var.roadrunner_namespace
  }

  data = {
    "access-key-id"     = var.aws_access_key_id
    "secret-access-key" = var.aws_secret_access_key
  }
}
