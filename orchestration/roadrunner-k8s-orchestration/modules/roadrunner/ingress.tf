resource "kubernetes_ingress_v1" "roadrunner_ingress" {

  metadata {
    name = "roadrunner-ingress"
    namespace = var.roadrunner_namespace
    # Configures annotations dynamically based on the environment (Minikube or EKS).
    # Minikube uses NGINX, while EKS uses an Application Load Balancer (ALB).
    annotations = terraform.workspace == "minikube" ? {
      "kubernetes.io/ingress.class"                = "nginx"
      "kubernetes.io/ingress.allow-http"           = "true"
      "nginx.ingress.kubernetes.io/ssl-redirect"   = "true"
      "nginx.ingress.kubernetes.io/configuration-snippet" = "more_set_headers \"Content-Security-Policy: default-src 'self'; script-src 'self' 'unsafe-eval' 'unsafe-inline' https://api.mapbox.com https://cdn.jsdelivr.net https://maps.googleapis.com https://*.googleapis.com https://maps.gstatic.com; style-src 'self' 'unsafe-inline' https://api.mapbox.com https://api.tiles.mapbox.com https://cdn.jsdelivr.net https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com https://cdn.jsdelivr.net; img-src 'self' blob: data: https://api.mapbox.com https://api.tiles.mapbox.com https://tarterware.com https://*.tarterware.com https://*.tarterware.info https://*.googleusercontent.com https://*.googleapis.com https://*.gstatic.com https://*.ggpht.com https://*.google.com; connect-src 'self' https://*.amazonaws.com https://*.amazoncognito.com https://cdn.jsdelivr.net https://api.mapbox.com https://api.tiles.mapbox.com https://events.mapbox.com https://ipapi.co https://*.tarterware.info https://*.tarterware.com https://*.googleapis.com https://*.google.com https://*.gstatic.com; worker-src 'self' blob:; child-src 'self' blob:;\";"
    } : {
      "alb.ingress.kubernetes.io/scheme"           = "internet-facing"
      "alb.ingress.kubernetes.io/target-type"      = "ip"
      "alb.ingress.kubernetes.io/listen-ports"     = "[{\"HTTPS\": 443}]"
      "alb.ingress.kubernetes.io/certificate-arn"  = var.tarterware_cert_arn
      "alb.ingress.kubernetes.io/ssl-policy"       = "ELBSecurityPolicy-2016-08"
      "alb.ingress.kubernetes.io/group.name"       = "shared-alb"
      "alb.ingress.kubernetes.io/healthcheck-path" = "/actuator/health"
      "alb.ingress.kubernetes.io/wafv2-acl-arn"    = aws_wafv2_web_acl.roadrunner_waf[0].arn
    }
  }

  spec {
    # Sets the ingress class dynamically: NGINX for Minikube, ALB for EKS.
    ingress_class_name = terraform.workspace == "minikube" ? "nginx" : "alb"

    # Defines rules for Minikube environment.
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

    # Defines rules for EKS environment.
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

    # Configures TLS settings dynamically based on the environment.
    # Minikube uses a local hostname, while EKS uses a public domain.
    tls {
      hosts = terraform.workspace == "minikube" ? ["roadrunner.tarterware.info"] : ["roadrunner.tarterware.com"]
      secret_name = terraform.workspace == "minikube" ? "roadrunner.tarterware.info-tls" : "roadrunner.tarterware.com-tls"
    }
  }
}

