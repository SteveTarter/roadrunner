resource "kubernetes_namespace" "strimzi" {
  metadata {
    name = var.namespace
  }
}

resource "helm_release" "strimzi_operator" {
  name             = "strimzi-kafka-operator"
  namespace        = kubernetes_namespace.strimzi.metadata[0].name
  repository       = "https://strimzi.io/charts/"
  chart            = "strimzi-kafka-operator"
  version          = var.chart_version
  create_namespace = false
  timeout          = 600
  wait             = true

  values = [
    yamlencode({
      watchAnyNamespace = false
      watchNamespaces   = [var.watch_namespace]
    })
  ]
}
