resource "kubernetes_ingress_v1" "roadrunner_view_ingress" {
  metadata {
    name = "roadrunner-view-ingress"
    namespace = var.roadrunner_namespace
    # Configures annotations dynamically based on the environment (Minikube or EKS).
    # Minikube uses NGINX, while EKS uses an Application Load Balancer (ALB).
    annotations = terraform.workspace == "minikube" ? {
      "kubernetes.io/ingress.class"              = "nginx"
      "kubernetes.io/ingress.allow-http"         = "true"
      "nginx.ingress.kubernetes.io/ssl-redirect" = "true"
    } : {
      "alb.ingress.kubernetes.io/scheme"          = "internet-facing"
      "alb.ingress.kubernetes.io/target-type"     = "ip"
      "alb.ingress.kubernetes.io/listen-ports"    = "[{\"HTTPS\": 443}]"
      "alb.ingress.kubernetes.io/certificate-arn" = var.tarterware_cert_arn
      "alb.ingress.kubernetes.io/ssl-policy"      = "ELBSecurityPolicy-2016-08"
      "alb.ingress.kubernetes.io/group.name"      = "shared-alb"
    }
  }

  spec {
    # Sets the ingress class dynamically: NGINX for Minikube, ALB for EKS.
    ingress_class_name = terraform.workspace == "minikube" ? "nginx" : "alb"

    # Defines rules for Minikube environment.
    dynamic "rule" {
      for_each = terraform.workspace == "minikube" ? [true] : []
      content {
        host = "roadrunner-view.tarterware.info"

        http {
          path {
            path     = "/"
            path_type = "Prefix"

            backend {
              service {
                name = kubernetes_service.roadrunner_view_service.metadata[0].name
                port {
                  number = 13000
                }
              }
            }
          }
        }
      }
    }

    # Defines rules for EKS environment.
    dynamic "rule" {
      for_each = terraform.workspace == "eks" ? [true] : []
      content {
        host = "roadrunner-view.tarterware.com"

        http {
          path {
            path     = "/"
            path_type = "Prefix"

            backend {
              service {
                name = kubernetes_service.roadrunner_view_service.metadata[0].name
                port {
                  number = 13000
                }
              }
            }
          }
        }
      }
    }

    # Configures TLS settings dynamically based on the environment.
    # Minikube uses a local hostname, while EKS uses a public domain.
    tls {
      hosts = terraform.workspace == "minikube" ? ["roadrunner-view.tarterware.info"] : ["roadrunner-view.tarterware.com"]
      secret_name = terraform.workspace == "minikube" ? "roadrunner-view.tarterware.info-tls" : "roadrunner-view.tarterware.com-tls"
    }
  }
}

