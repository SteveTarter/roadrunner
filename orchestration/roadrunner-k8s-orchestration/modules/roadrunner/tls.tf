resource "kubernetes_secret" "roadrunner_info_tls" {
  count  = terraform.workspace == "minikube" ? 1 : 0

  metadata {
    name      = "roadrunner.tarterware.info-tls"
    namespace = var.roadrunner_namespace
  }

  type = "kubernetes.io/tls"

  data = {
    "tls.crt" = file("${path.module}/../../certs/roadrunner.tarterware.info.pem")
    "tls.key" = file("${path.module}/../../certs/roadrunner.tarterware.info.key")
  }
}

