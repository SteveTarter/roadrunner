resource "kubernetes_secret" "roadrunner_info_tls" {
  count  = terraform.workspace == "minikube" ? 1 : 0 # Creates the secret only in Minikube workspace

  metadata {
    name      = "roadrunner.tarterware.info-tls"
    namespace = var.roadrunner_namespace # Assigns the secret to the appropriate namespace
  }

  type = "kubernetes.io/tls" # Specifies the secret type for TLS certificates

  data = {
    "tls.crt" = file("${path.module}/../../certs/roadrunner.tarterware.info.pem") # Path to the TLS certificate
    "tls.key" = file("${path.module}/../../certs/roadrunner.tarterware.info.key") # Path to the TLS private key
  }
}

