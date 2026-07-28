resource "kubernetes_role" "secret_reader" {
  metadata {
    name      = "secret-reader"
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }


  rule {
    api_groups = [""]
    resources  = ["secrets"]
    verbs      = ["get"]
  }
}
