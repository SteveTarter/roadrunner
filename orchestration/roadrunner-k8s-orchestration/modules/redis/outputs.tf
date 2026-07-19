output "redis_host" {
  value = "${helm_release.redis.name}-master.${var.roadrunner_namespace}.svc.cluster.local"
}

output "redis_password" {
  value = random_password.redis_pwd.result
  sensitive = true
}
