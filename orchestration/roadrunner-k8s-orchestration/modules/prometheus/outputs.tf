output "prometheus_release_name" {
  value = helm_release.kube_prometheus_stack.name
  description = "The name of the Prometheus Helm release"
}

output "monitoring_namespace" {
  value       = kubernetes_namespace.monitoring.metadata[0].name
  description = "The name of the monitoring namespace"
}

