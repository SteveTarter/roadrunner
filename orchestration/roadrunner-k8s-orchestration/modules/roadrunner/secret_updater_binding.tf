resource "kubernetes_role_binding" "secret_updater_binding" {
  metadata {
    name      = "secret-updater-binding"
    namespace = "monitoring"
  }

  subject {
    kind      = "ServiceAccount"
    name      = "default"  # Change this if you use a different service account.
    namespace = "roadrunner"
  }

  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.secret_updater.metadata[0].name
  }
}

