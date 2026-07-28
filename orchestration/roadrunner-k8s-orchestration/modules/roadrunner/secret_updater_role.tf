resource "kubernetes_role" "secret_updater" {
  metadata {
    name      = "secret-updater"
    namespace = var.monitoring_namespace
  }


  rule {
    api_groups = [""]
    resources  = ["secrets"]
    verbs      = ["get", "create", "update", "patch"]
  }
}

