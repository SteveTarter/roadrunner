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
spring.application.name=roadrunner
spring.data.rest.base-path=/
com.tarterware.redis.host=${var.redis_host}
com.tarterware.redis.port=6379
com.tarterware.redis.password=${var.redis_password}
com.tarterware.roadrunner.vehicle-update-period=250ms
com.tarterware.roadrunner.jitter-stat-capacity=200
logging.level.org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer=WARN
logging.level.org.apache.kafka.clients.consumer.internals.SubscriptionState=WARN
spring.profiles.active=${terraform.workspace}
spring.kafka.bootstrap-servers=${var.kafka_bootstrap_servers}
spring.kafka.listener.concurrency=3
spring.kafka.producer.acks=all
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.tarterware.roadrunner.messaging
spring.kafka.consumer.properties.spring.json.use.type.headers=true
spring.kafka.consumer.properties.spring.json.value.default.type=com.tarterware.roadrunner.messaging.VehiclePositionEvent
spring.kafka.consumer.properties.heartbeat.interval.ms=3000
spring.kafka.consumer.properties.session.timeout.ms=10000
com.tarterware.roadrunner.messaging.kafka.enabled=true
com.tarterware.roadrunner.kafka.topics.vehicle-position=${var.kafka_topic_vehicle_position}
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
spring.security.oauth2.resourceserver.jwt.issuer-uri=${var.cognito_authority}
cognito.app-client-id=${var.cognito_client_id}
prometheus.secret.namespace=monitoring
prometheus.secret.name=prometheus-token-secret
EOT
  }
}

