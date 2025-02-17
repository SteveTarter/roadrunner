resource "kubernetes_deployment" "roadrunner" {
  metadata {
    name = "roadrunner"
    namespace = var.roadrunner_namespace
    labels = {
      app = "roadrunner"
    }
  }

  spec {
    replicas                = 1
    revision_history_limit  = 2

    selector {
      match_labels = {
        app = "roadrunner"
      }
    }

    template {
      metadata {
        labels = {
          app = "roadrunner"
        }
      }

      spec {
        container {
          name  = "roadrunner"
          image = "tarterware/roadrunner:latest"
          image_pull_policy = "Always"

          port {
            container_port = 8080
          }

          # Redis password is only needed on minikube; eks uses IAM
          dynamic "env" {
            for_each = terraform.workspace == "minikube" ? [1] : []
            content {
            name = "REDIS_PASSWORD"
              value_from {
                secret_key_ref {
                  name = "redis"
                  key = "redis-password"
                }
              }
            }
          }

          # Dynamic args
          args = terraform.workspace == "minikube" ? [
            "--com.tarterware.redis.password=$(REDIS_PASSWORD)"
          ] : []

          volume_mount {
            name       = "application-conf"
            mount_path = "/config/application.properties"
            sub_path   = "application.properties"
          }

          resources {
            requests = {
              memory = "256Mi"
              cpu    = "250m"
            }
            limits = {
              memory = "512Mi"
              cpu    = "500m"
            }
          }
        }

        volume {
          name = "application-conf"

          config_map {
            name = "roadrunner-config"

            items {
              key  = "application.properties"
              path = "application.properties"
            }
          }
        }
      }
    }
  }
}

