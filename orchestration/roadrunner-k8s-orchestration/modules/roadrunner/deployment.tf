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
        # Ensure the mile-weaver subdirectory is created
        init_container {
          name  = "setup-dir"
          image = "busybox:latest"
          command = [
            "sh", "-c", "mkdir -p /mnt/data/roadrunner-data && chmod 777 /mnt/data/roadrunner-data"
          ]

          volume_mount {
            name       = "tarterware-data"
            mount_path = "/mnt/data"
          }
        }

        container {
          name  = "roadrunner"
          image = "tarterware/roadrunner:latest"
          image_pull_policy = "Always"

          port {
            container_port = 8080
            protocol       = "TCP"
          }

          volume_mount {
            name       = "application-conf"
            mount_path = "/config/application.properties"
            sub_path   = "application.properties"
          }

          volume_mount {
            name       = "tarterware-data"
            mount_path = var.tarterware_data_dir
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

        dynamic "volume" {
          for_each = terraform.workspace == "minikube" ? [1] : []
          content {
            name = "tarterware-data"

            host_path {
              path = var.tarterware_data_dir
              type = "Directory"
            }
          }
        }

        dynamic "volume" {
          for_each = terraform.workspace == "eks" ? [1] : []
          content {
            name = "tarterware-data"

            persistent_volume_claim {
              claim_name = "roadrunner-files-efs-pvc"
            }
          }
        }
      }
    }
  }
}

