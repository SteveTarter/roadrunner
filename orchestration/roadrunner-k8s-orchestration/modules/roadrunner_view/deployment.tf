resource "kubernetes_deployment" "roadrunner_view_deployment" {
  metadata {
    name      = "roadrunner-view"
    namespace = var.roadrunner_namespace
    labels = {
      app = "roadrunner-view"
    }
  }

  spec {
    replicas                = 1
    revision_history_limit  = 2

    selector {
      match_labels = {
        app = "roadrunner-view"
      }
    }

    template {
      metadata {
        labels = {
          app = "roadrunner-view"
        }
      }

      spec {
        security_context {
          run_as_non_root = true
          run_as_user     = 101 # User ID 101 is standard for Nginx unprivileged images
          fs_group        = 101
        }

        # Create a shared temporary volume to pass the JS file between containers
        volume {
          name = "config-volume"
          empty_dir {}
        }

        # Add backing empty_dir volumes for Nginx
        volume {
          name = "nginx-cache"
          empty_dir {}
        }
        volume {
          name = "nginx-pid"
          empty_dir {}
        }

        # Init Container to generate the dynamic JS config file
        init_container {
          name  = "config-generator"
          image = "busybox"
          
          security_context {
            read_only_root_filesystem  = true
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
          }

          # This script finds all env vars starting with REACT_APP_ and builds a JS object.
          # Note the double dollar signs ($$) which escape Terraform interpolation 
          # to ensure the shell handles the variable expansion at runtime.
          command = [
            "sh", "-c",
            <<-EOT
            echo "window._env_ = {" > /tmp/env-config.js
            env | grep '^REACT_APP_' | awk -F= '{print "  " $$1 ": \"" $$2 "\","}' >> /tmp/env-config.js
            echo "};" >> /tmp/env-config.js
            EOT
          ]

          # Load the variables from your ConfigMap so the script can find them
          env_from {
            config_map_ref {
              name = "roadrunner-view-config"
            }
          }

          volume_mount {
            name       = "config-volume"
            mount_path = "/tmp"
          }
        }

        container {
          name  = "roadrunner-view"
          image = "tarterware/roadrunner-view:${var.roadrunner_view_version}"
          image_pull_policy = "Always"

          security_context {
            read_only_root_filesystem  = true
            allow_privilege_escalation = false
            capabilities {
              drop = ["ALL"]
            }
          }

          resources {
            requests = {
              cpu    = "50m"
              memory = "32Mi"
            }
            limits = {
              cpu    = "100m"
              memory = "128Mi"
            }
          }

          port {
            container_port = 80
          }

          # Mount the Nginx directories as ephemeral writable volumes
          volume_mount {
            name       = "nginx-cache"
            mount_path = "/var/cache/nginx"
          }
          volume_mount {
            name       = "nginx-pid"
            mount_path = "/var/run"
          }

          # Mount the generated config file into the Nginx web root
          volume_mount {
            name       = "config-volume"
            mount_path = "/usr/share/nginx/html/env-config.js"
            sub_path   = "env-config.js"
          }

          env_from {
            config_map_ref {
              name = "roadrunner-view-config"
            }
          }
        }
      }
    }
  }
}

