resource "kubernetes_config_map" "roadrunner_config" {
  metadata {
    name = "roadrunner-config"
    namespace = var.roadrunner_namespace
  }

  data = {
    "application.properties" = <<EOT
mapbox.api.key=${var.mapbox_api_key}
mapbox.api.url=https://api.mapbox.com/
spring.data.rest.base-path=/
com.tarterware.data-dir=/tarterware-data
spring.web.allow-cors=true
spring.security.oauth2.resourceserver.jwt.issuer-uri=${var.auth0_api_issuer_url}
EOT
  }
}

