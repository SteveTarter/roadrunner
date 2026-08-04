resource "terraform_data" "topics" {
  for_each = var.topics

  input = {
    yaml = yamlencode({
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
    name         = each.key
    kube_context = var.kube_context
    namespace    = var.namespace
  }

  provisioner "local-exec" {
    command = "echo '${self.input.yaml}' | kubectl apply -f - --context=${self.input.kube_context} -n ${self.input.namespace}"
  }

  provisioner "local-exec" {
    when    = destroy
    command = "kubectl delete KafkaTopic/${self.input.name} -n ${self.input.namespace} --context=${self.input.kube_context} --ignore-not-found"
  }
}
