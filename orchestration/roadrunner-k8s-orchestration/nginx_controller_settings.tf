resource "kubernetes_config_map_v1_data" "nginx_controller_settings" {
  count       = terraform.workspace == "minikube" ? 1 : 0
  metadata {
    name      = "ingress-nginx-controller"
    namespace = "ingress-nginx"
  }

  # This binds the custom header block globally to the controller pipeline
  data = {
    "add-headers" = "${kubernetes_config_map.nginx_custom_security_headers[0].metadata[0].namespace}/${kubernetes_config_map.nginx_custom_security_headers[0].metadata[0].name}"
  }
}
