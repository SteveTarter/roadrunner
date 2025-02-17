resource "kubernetes_service" "roadrunner_service" {
  metadata {
    name = "roadrunner"
    namespace = var.roadrunner_namespace # Namespace dynamically defined for resource organization
    labels = {
      name = "roadrunner"
    }
  }

  spec {
    type = "ClusterIP"

    selector = {
      app = "roadrunner"
    }

    port {
      name        = "http-metrics"
      port        = 18280 # Service port
      target_port = 8080  # Application's internal listening port
    }
  }
}

