resource "kubernetes_service" "roadrunner_service" {
  metadata {
    name = "roadrunner"
    namespace = var.roadrunner_namespace # Namespace dynamically defined for resource organization
    labels = {
      name = "roadrunner"
    }
  }

  spec {
    type = "NodePort" # NodePort used for external access in local setups like Minikube

    selector = {
      app = "roadrunner"
    }

    port {
      port        = 18280 # Service port
      target_port = 8080  # Application's internal listening port
    }
  }
}

