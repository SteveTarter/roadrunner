resource "kubernetes_service" "roadrunner_service" {
  metadata {
    name = "roadrunner"
    namespace = var.roadrunner_namespace
    labels = {
      name = "roadrunner"
    }
  }

  spec {
    type = "NodePort"

    selector = {
      app = "roadrunner"
    }

    port {
      port        = 18280
      target_port = 8080
      name        = "roadrunner-service-port"
    }
  }
}

