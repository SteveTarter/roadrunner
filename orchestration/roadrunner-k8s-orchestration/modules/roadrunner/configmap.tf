# This ConfigMap provides configuration properties for the Roadrunner application,
# enabling connectivity to external services like Mapbox and Auth0, and defining
# application-specific settings.

resource "kubernetes_config_map" "roadrunner_config" {
  metadata {
    name = "roadrunner-config"
    # The namespace is dynamically determined from the variable to ensure separation
    # and organization of resources within the Kubernetes cluster.
    namespace = var.roadrunner_namespace
  }

  data = {
    "application.properties" = <<EOT
mapbox.api.key=${var.mapbox_api_key}
mapbox.api.url=https://api.mapbox.com/
spring.data.rest.base-path=/
com.tarterware.redis.host=${var.redis_host}
com.tarterware.redis.port=6379
spring.profiles.active=${terraform.workspace}
management.endpoints.web.exposure.include=health,info
spring.security.oauth2.resourceserver.jwt.issuer-uri=${var.auth0_api_issuer_url}
EOT
  }
}

