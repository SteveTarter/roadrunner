resource "kubernetes_config_map" "nginx_custom_security_headers" {
  count       = terraform.workspace == "minikube" ? 1 : 0
  metadata {
    name      = "custom-security-headers"
    namespace = "ingress-nginx"
  }

  data = {
    "X-Frame-Options"           = "DENY"
    "X-Content-Type-Options"     = "nosniff"
    "Strict-Transport-Security" = "max-age=31536000; includeSubDomains"
  }
}
