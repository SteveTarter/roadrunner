resource "helm_release" "redis" {
  name       = "redis"
  namespace  = var.roadrunner_namespace
  chart      = "redis"
  repository = "https://charts.bitnami.com/bitnami"
  version    = "19.4.0"

  # Set values (equivalent to --set in Helm CLI)
  set {
    name  = "replica.replicaCount"
    value = "1"
  }
}
