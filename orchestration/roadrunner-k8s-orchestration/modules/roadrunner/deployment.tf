resource "kubernetes_deployment" "roadrunner" {
  metadata {
    name = "roadrunner"
    namespace = var.roadrunner_namespace
    labels = {
      app = "roadrunner"
    }
  }

  spec {
    replicas = 1

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

          env {
            name = "REDIS_PASSWORD"
            value_from {
              secret_key_ref {
                name = "${helm_release.redis.name}"
                namespace = var.roadrunner_namespace
                key = "redis"
              }
            }
          }

          args = [
            "--com.tarterware.redis.password=${REDIS_PASSWORD}"
          ]

          volume_mount {
            name       = "application-conf"
            mount_path = "/config/application.properties"
            sub_path   = "application.properties"
          }

          resources {
            requests = {
              memory = "512Mi"
              cpu    = "500m"
            }
            limits = {
              memory = "1Gi"
              cpu    = "1"
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

