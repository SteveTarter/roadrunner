terraform {
  required_providers {
    kubectl = {
      source  = "gavinbunney/kubectl"
      version = "~> 1.14.0"
    }
  }
}

resource "kubectl_manifest" "kafka_nodepool" {
  count     = var.enabled ? 1 : 0
  yaml_body = yamlencode({
    apiVersion = "kafka.strimzi.io/v1beta2"
    kind       = "KafkaNodePool"
    metadata = {
      name      = "default"
      namespace = var.namespace
      labels = {
        "strimzi.io/cluster" = var.cluster_name
      }
    }
    spec = {
      replicas = var.replicas
      roles    = ["broker", "controller"]
      storage = var.storage_type == "persistent-claim" ? merge(
        {
          type        = "persistent-claim"
          size        = var.storage_size
          deleteClaim = false
        },
        var.storage_class != null ? {
          class = var.storage_class
        } : {}
      ) : merge(
        {
          type = "ephemeral"
        },
        var.storage_size_limit != null ? {
          sizeLimit = var.storage_size_limit
        } : {}
      )
    }
  })

  depends_on = [var.operator_dependency]
}

resource "kubectl_manifest" "kafka_cluster" {
  count     = var.enabled ? 1 : 0
  yaml_body = yamlencode({
    apiVersion = "kafka.strimzi.io/v1beta2"
    kind       = "Kafka"
    metadata = {
      name      = var.cluster_name
      namespace = var.namespace
      annotations = {
        "strimzi.io/node-pools" = "enabled"
        "strimzi.io/kraft"      = "enabled"
      }
    }
    spec = {
      kafka = {
        version         = var.kafka_version
        listeners = [
          {
            name = "plain"
            port = 9092
            type = "internal"
            tls  = false
          }
        ]
        config = {
          "offsets.topic.replication.factor"         = 1
          "transaction.state.log.replication.factor" = 1
          "transaction.state.log.min.isr"            = 1
          "default.replication.factor"               = 1
          "min.insync.replicas"                      = 1
        }
      }
      entityOperator = {
        topicOperator = {}
        userOperator  = {}
      }
    }
  })

  depends_on = [kubectl_manifest.kafka_nodepool]
}
