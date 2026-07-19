output "name" {
  value = kubectl_manifest.kafka_cluster.name
}

output "bootstrap_servers" {
  value = "${kubectl_manifest.kafka_cluster.name}-kafka-bootstrap.${var.namespace}.svc.cluster.local:9092"
}
