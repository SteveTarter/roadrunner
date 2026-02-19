resource "helm_release" "redis" {
  name       = "redis"
  namespace  = var.roadrunner_namespace
  chart      = "redis"
  repository = "oci://registry-1.docker.io/bitnamicharts"
  version    = "25.3.0"

  # Set values (equivalent to --set in Helm CLI)
  set = [
    {
      name  = "replica.replicaCount"
      value = "1"
    }
  ]
}
