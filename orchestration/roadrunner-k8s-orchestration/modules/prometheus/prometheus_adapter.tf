resource "helm_release" "prometheus_adapter" {
  name       = "prometheus-adapter"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "prometheus-adapter"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name

  values = [
    file("${path.module}/prometheus-adapter-values.yaml")
  ]

  depends_on = [helm_release.kube_prometheus_stack]
}
