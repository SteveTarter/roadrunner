resource "random_password" "redis_pwd" {
  length  = 16
  special = false
}

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
    },
    {
      name  = "master.persistence.enabled"
      value = false
    },
    {
      name  = "replica.persistence.enabled"
      value = false
    },
    {
      name  = "replica.readinessProbe.timeoutSeconds"
      value = "5"
    },
    {
      name  = "replica.readinessProbe.failureThreshold"
      value = "10"
    },
    {
      name  = "replica.readinessProbe.initialDelaySeconds"
      value = "20"
    }
  ]

  set_sensitive = [
    {
      name  = "auth.password"
      value = random_password.redis_pwd.result
    }
  ]
}

data "kubernetes_secret_v1" "redis" {
  metadata {
    name      = "redis"
    namespace = var.roadrunner_namespace
  }

  depends_on = [helm_release.redis]
}
