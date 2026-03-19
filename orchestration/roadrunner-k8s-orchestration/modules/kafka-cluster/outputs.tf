output "name" {
  value = kubectl_manifest.kafka_cluster[0].name
}
# output "bootstrap_endpoint" {
#   value = module.kafka_cluster.kubectl_manifest.kafka_cluster[0].
# }
