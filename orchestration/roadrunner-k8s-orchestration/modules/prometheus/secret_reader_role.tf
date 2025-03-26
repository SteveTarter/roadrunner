resource "kubernetes_role" "secret_reader" {
  metadata {
    name      = "secret-reader"
    namespace = "monitoring"
  }

  rule {
    api_groups = [""]
    resources  = ["secrets"]
    verbs      = ["get"]
  }
}
