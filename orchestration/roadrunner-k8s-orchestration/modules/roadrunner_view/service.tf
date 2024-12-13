resource "kubernetes_service" "roadrunner_view_service" {
  metadata {
    name      = "roadrunner-view"
    namespace = var.roadrunner_namespace # Namespace dynamically defined for resource organization
  }

  spec {
    type = "NodePort" # NodePort used for external access in local setups like Minikube

    selector = {
      app = "roadrunner-view"
    }

    port {
      port        = 13000 # Service port
      target_port = 3000  # Application's internal listening port
    }
  }
}

