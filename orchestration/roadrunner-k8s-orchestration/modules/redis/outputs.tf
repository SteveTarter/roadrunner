output "redis_host" {
  value = "${helm_release.redis.name}-master.${var.roadrunner_namespace}.svc.cluster.local"
}

