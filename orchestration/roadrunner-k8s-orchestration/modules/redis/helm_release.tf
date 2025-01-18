resource "helm_release" "redis" {
  name       = "redis"
  namespace  = var.roadrunner_namespace
  chart      = "redis"
  repository = "https://charts.bitnami.com/bitnami"

  # Set values (equivalent to --set in Helm CLI)
  set {
    name  = "replica.replicaCount"
    value = "1"
  }
}
