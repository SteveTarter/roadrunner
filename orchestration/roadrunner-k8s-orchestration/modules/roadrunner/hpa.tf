resource "kubernetes_horizontal_pod_autoscaler_v2" "roadrunner_hpa" {
  metadata {
    name      = "roadrunner-hpa"
    namespace = var.roadrunner_namespace
  }

  spec {
    scale_target_ref {
      api_version = "apps/v1"
      kind        = "Deployment"
      name        = "roadrunner"
    }

    min_replicas = 2
    max_replicas = 5

    metric {
      type = "Pods"
      pods {
        metric {
          name = "roadrunner_mean_jitter_time_milliseconds"
        }
        target {
          type          = "AverageValue"
          average_value = "100m"  # 100 milliseconds threshold
        }
      }
    }

    behavior {
      scale_up {
        stabilization_window_seconds = 60
        select_policy                = "Max"
        policy {
          type           = "Pods"
          value          = 2
          period_seconds = 60
        }
      }

      scale_down {
        stabilization_window_seconds = 120
        select_policy                = "Min"
        policy {
          type           = "Pods"
          value          = 1
          period_seconds = 60
        }
      }
    }
  }
  depends_on = [kubernetes_manifest.roadrunner_service_monitor]
}

