resource "kubernetes_role_binding" "secret_reader_binding" {
  metadata {
    name      = "secret-reader-binding"
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }

  subject {
    kind      = "ServiceAccount"
    name      = "prometheus"
    namespace = kubernetes_namespace.monitoring.metadata[0].name
  }


  role_ref {
    api_group = "rbac.authorization.k8s.io"
    kind      = "Role"
    name      = kubernetes_role.secret_reader.metadata[0].name
  }
}
