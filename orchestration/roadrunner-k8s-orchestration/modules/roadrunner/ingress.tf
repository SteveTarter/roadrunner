resource "kubernetes_ingress_v1" "roadrunner_ingress" {

  metadata {
    name = "roadrunner-ingress"
    namespace = var.roadrunner_namespace
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
    ingress_class_name = terraform.workspace == "minikube" ? "nginx" : "alb"

    dynamic "rule" {
      for_each = terraform.workspace == "minikube" ? [true] : []
      content {
        host = "roadrunner.tarterware.info"

        http {
          path {
            path     = "/"
            path_type = "Prefix"

            backend {
              service {
                name = kubernetes_service.roadrunner_service.metadata[0].name
                port {
                  number = 18280
                }
              }
            }
          }
        }
      }
    }

    dynamic "rule" {
      for_each = terraform.workspace == "eks" ? [true] : []
      content {
        host = "roadrunner.tarterware.com"

        http {
          path {
            path     = "/"
            path_type = "Prefix"

            backend {
              service {
                name = kubernetes_service.roadrunner_service.metadata[0].name
                port {
                  number = 18280
                }
              }
            }
          }
        }
      }
    }

    tls {
      hosts = terraform.workspace == "minikube" ? ["roadrunner.tarterware.info"] : ["roadrunner.tarterware.com"]
      secret_name = terraform.workspace == "minikube" ? "roadrunner.tarterware.info-tls" : "roadrunner.tarterware.com-tls"
    }
  }
}

