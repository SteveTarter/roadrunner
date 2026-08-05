output "db_host" {
  description = "The database host endpoint."
  value       = terraform.workspace == "eks" ? "postgis-rds-placeholder" : "postgis-postgresql.${var.roadrunner_namespace}.svc.cluster.local"
}

output "db_port" {
  description = "The database port."
  value       = 5432
}

output "db_name" {
  description = "The name of the database."
  value       = var.db_name
}

output "db_username" {
  description = "The database username."
  value       = var.db_username
}

output "db_password_secret_name" {
  description = "The name of the Kubernetes secret containing the database credentials."
  value       = "roadrunner-postgis-secret"
}
