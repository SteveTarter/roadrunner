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
com.tarterware.roadrunner.vehicle-polling-period=100ms
com.tarterware.roadrunner.vehicle-update-period=250ms
com.tarterware.roadrunner.jitter-stat-capacity=200
spring.profiles.active=${terraform.workspace}
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
spring.security.oauth2.resourceserver.jwt.issuer-uri=${var.auth0_api_issuer_url}
auth0.api.audience=${var.auth0_api_audience}
auth0.api.client-id=${var.auth0_api_client_id}
auth0.api.client-secret=${var.auth0_api_client_secret}
prometheus.secret.namespace=monitoring
prometheus.secret.name=prometheus-token-secret
logging.level.com.tarterware.roadrunner.components.VehicleManager=INFO
EOT
  }
}

