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
        service_account_name = kubernetes_service_account.roadrunner_sa.metadata[0].name

        security_context {
          run_as_non_root = true
          run_as_user     = 1000
          fs_group        = 1000
        }

        container {
          name  = "roadrunner"
          image = "tarterware/roadrunner:${var.roadrunner_version}"
          image_pull_policy = "Always"

          security_context {
            read_only_root_filesystem  = true
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
          }

          port {
            container_port = 8080
          }

          # Standard JVM environmental flags to guarantee correct memory allocation during boot
          env {
            name  = "JAVA_TOOL_OPTIONS"
            value = "-XX:MaxDirectMemorySize=600M -Xms128M -Xmx256M"
          }

          env {
            name  = "AWS_REGION"
            value = var.region
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

          # AWS Secrets for Minikube
          dynamic "env" {
            for_each = terraform.workspace == "minikube" ? [1] : []
            content {
              name = "AWS_ACCESS_KEY_ID"
              value_from {
                secret_key_ref {
                  name = "aws-credentials"
                  key  = "access-key-id"
                }
              }
            }
          }

          dynamic "env" {
            for_each = terraform.workspace == "minikube" ? [1] : []
            content {
              name = "AWS_SECRET_ACCESS_KEY"
              value_from {
                secret_key_ref {
                  name = "aws-credentials"
                  key  = "secret-access-key"
                }
              }
            }
          }

          env {
            name = "K8S_POD_NAME"
            value_from {
              field_ref {
                field_path = "metadata.name"
              }
            }
          }

          # Dynamic args
          args = terraform.workspace == "minikube" ? [
            "--com.tarterware.redis.password=$(REDIS_PASSWORD)"
          ] : []

          # Add volume mount for /tmp to allow Spring Boot to write ephemeral files
          volume_mount {
            mount_path = "/tmp"
            name       = "tmp-volume"
          }

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
              cpu    = "1000m"
            }
          }

        }

        # Add the backing emptyDir volume for /tmp
        volume {
          name = "tmp-volume"
          empty_dir {}
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

