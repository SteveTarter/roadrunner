output "prometheus_release_name" {
  value = helm_release.kube_prometheus_stack.name
  description = "The name of the Prometheus Helm release"
}
