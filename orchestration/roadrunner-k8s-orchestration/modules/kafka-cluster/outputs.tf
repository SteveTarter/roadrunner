output "name" {
  value = var.cluster_name
}

output "bootstrap_servers" {
  value = "${var.cluster_name}-kafka-bootstrap.${var.namespace}.svc.cluster.local:9092"
}
