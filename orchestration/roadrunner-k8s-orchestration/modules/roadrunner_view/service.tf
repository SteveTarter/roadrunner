resource "kubernetes_service" "roadrunner_view_service" {
  metadata {
    name      = "roadrunner-view"
    namespace = var.roadrunner_namespace
  }

  spec {
    type = "NodePort"

    selector = {
      app = "roadrunner-view"
    }

    port {
      port        = 13000
      target_port = 3000
    }
  }
}

