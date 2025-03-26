resource "helm_release" "kube_prometheus_stack" {
  name       = "kube-prometheus-stack"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name

  values = [
    <<-EOT
      prometheus:
        prometheusSpec:
          serviceMonitorNamespaceSelector: {}  # Allow ServiceMonitors from all namespaces
          serviceMonitorSelector: # Select the service monitors
            matchLabels:
              release: kube-prometheus-stack
    EOT
  ]
}

