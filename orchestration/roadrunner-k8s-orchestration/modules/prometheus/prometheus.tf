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

      # Fix for Minikube / Python 3.14 SSL strictness
      grafana:
        sidecar:
          image:
            tag: 1.28.0  # Pinning to a stable version with older Python
          dashboards:
            enabled: true
          datasources:
            enabled: true
            # Applying the pin here as well to prevent the same error
            image:
              tag: 1.28.0
    EOT
  ]
}

