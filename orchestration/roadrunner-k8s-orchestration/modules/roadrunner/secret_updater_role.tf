resource "kubernetes_role" "secret_updater" {
  metadata {
    name      = "secret-updater"
    namespace = "monitoring"
  }

  rule {
    api_groups = [""]
    resources  = ["secrets"]
    verbs      = ["get", "create", "update", "patch"]
  }
}

