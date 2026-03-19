terraform {
  required_providers {
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.14.0"
    }
  }
}

resource "kubectl_manifest" "topics" {
  for_each = var.enabled ? var.topics : {}

  yaml_body = yamlencode({
    apiVersion = "kafka.strimzi.io/v1"
    kind       = "KafkaTopic"
    metadata = {
      name      = each.key
      namespace = var.namespace
      labels = {
        "strimzi.io/cluster" = var.cluster_name
      }
    }
    spec = merge(
      {
        partitions = each.value.partitions
        replicas   = each.value.replicas
      },
      each.value.topic_name != null ? {
        topicName = each.value.topic_name
      } : {},
      length(each.value.config) > 0 ? {
        config = each.value.config
      } : {}
    )
  })
}
