resource "kubernetes_manifest" "roadrunner_service_monitor" {
  manifest = {
    apiVersion = "monitoring.coreos.com/v1"
    kind       = "ServiceMonitor"
    metadata = {
      name      = "roadrunner-monitor"
      namespace = "monitoring" # Application namespace
      labels = {
        release = "kube-prometheus-stack" # Prometheus release label
      }
    }
    spec = {
      namespaceSelector = {
        matchNames = [ "roadrunner" ]
      }
      selector = {
        matchLabels = {
          name = "roadrunner" # Application label
        }
      }
      endpoints = [
        {
          port = "http-metrics"  # Port application exposes metrics on
          path = "/actuator/prometheus" # The prometheus path
          interval = "10s" # Scrape interval
          authorization = {
            type = "Bearer"
            credentials = {
              name = "prometheus-token-secret"
              key  = "token"
            }
          }
        }
      ]
    }
  }
}

