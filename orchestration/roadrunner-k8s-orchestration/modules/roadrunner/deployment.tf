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
        # Ensures that a specific subdirectory exists on the mount point
        # before the main application container starts.
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

        # For Minikube: Uses host path for local storage.
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

        # For EKS: Uses a Persistent Volume Claim (PVC) backed by EFS.
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

