resource "terraform_data" "kafka_cluster" {
  input = {
    yaml_nodepool = yamlencode({
      apiVersion = "kafka.strimzi.io/v1"
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
    yaml_cluster = yamlencode({
      apiVersion = "kafka.strimzi.io/v1"
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
          version = var.kafka_version
          listeners = [
            {
              name = "plain"
              port = 9092
              type = "internal"
              tls  = false
            },
            {
              name = "external"
              port = 9094
              type = "nodeport"
              tls  = false
            }
          ]
          config = {
            "log.retention.ms"                         = 604800000
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
    cluster_name = var.cluster_name
    kube_context = var.kube_context
    namespace    = var.namespace
  }

  provisioner "local-exec" {
    command = <<EOT
      echo '${self.input.yaml_nodepool}' | kubectl apply -f - --context=${self.input.kube_context} -n ${self.input.namespace}
      echo '${self.input.yaml_cluster}' | kubectl apply -f - --context=${self.input.kube_context} -n ${self.input.namespace}
    EOT
  }

  provisioner "local-exec" {
    when    = destroy
    command = <<EOT
      kubectl delete Kafka/${self.input.cluster_name} -n ${self.input.namespace} --context=${self.input.kube_context} --ignore-not-found
      kubectl delete KafkaNodePool/default -n ${self.input.namespace} --context=${self.input.kube_context} --ignore-not-found
    EOT
  }

  depends_on = [var.operator_dependency]
}
