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
spring.application.name=roadrunner
spring.data.rest.base-path=/
spring.profiles.active=${terraform.workspace}
mapbox.api.key=${var.mapbox_api_key}
mapbox.api.url=https://api.mapbox.com/
management.server.port=8081
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=when-authorized
com.tarterware.redis.host=${var.redis_host}
com.tarterware.redis.port=6379
com.tarterware.redis.password=${var.redis_password}
com.tarterware.roadrunner.vehicle-update-period=250ms
com.tarterware.roadrunner.vehicle-state-buffer-period=2s
com.tarterware.roadrunner.jitter-stat-capacity=200
com.tarterware.roadrunner.playback-cache-timeout=1m
com.tarterware.roadrunner.playback-cache-size=100
com.tarterware.roadrunner.usage-limits.default-daily-vehicle-starts=30
com.tarterware.roadrunner.usage-limits.redis-key-prefix=roadrunner:usage:vehicle-starts
com.tarterware.roadrunner.usage-limits.counter-ttl-hours=24
com.tarterware.roadrunner.cors.allowed-origins=${var.allowed_cors_origins}
com.tarterware.roadrunner.messaging.kafka.enabled=true
com.tarterware.roadrunner.messaging.kafka.debug.enabled=false
prometheus.secret.namespace=monitoring
prometheus.secret.name=prometheus-token-secret
logging.level.org.apache.kafka.clients.consumer.internals.LegacyKafkaConsumer=WARN
logging.level.org.apache.kafka.clients.consumer.internals.SubscriptionState=WARN
logging.level.org.apache.kafka.clients.consumer.internals.ClassicKafkaConsumer=WARN
spring.kafka.bootstrap-servers=${var.kafka_bootstrap_servers}
spring.kafka.properties.socket.connection.setup.timeout.ms=30000
spring.kafka.properties.request.timeout.ms=60000
spring.kafka.listener.concurrency=3
spring.kafka.producer.acks=all
spring.kafka.producer.properties.enable.idempotence=true
spring.kafka.producer.properties.linger.ms=10
spring.kafka.producer.properties.batch.size=65536
spring.kafka.producer.properties.compression.type=lz4
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.max-poll-records=200
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
spring.kafka.consumer.properties.spring.deserializer.value.delegate.class=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties.spring.json.trusted.packages=com.tarterware.roadrunner.messaging
spring.kafka.consumer.properties.spring.json.use.type.headers=true
spring.kafka.consumer.properties.spring.json.value.default.type=com.tarterware.roadrunner.messaging.VehiclePositionEvent
spring.kafka.consumer.properties.heartbeat.interval.ms=3000
spring.kafka.consumer.properties.session.timeout.ms=10000
com.tarterware.roadrunner.kafka.topics.vehicle-position=${var.kafka_topic_vehicle_position}
spring.security.oauth2.resourceserver.jwt.issuer-uri=${var.cognito_authority}
cognito.app-client-id=${var.cognito_client_id}
com.tarterware.roadrunner.aws.cognito.user-pool-id=${var.cognito_user_pool_id}
spring.data.rest.cors.allowed-origins=${var.roadrunner_view_url_base}
spring.data.rest.cors.allowed-methods=GET,POST,PUT,DELETE,PATCH,OPTIONS
spring.data.rest.cors.allowed-headers=*
spring.data.rest.cors.allow-credentials=true
spring.data.rest.cors.max-age=3600
com.tarterware.roadrunner.frontend-url=${var.roadrunner_view_url_base}
EOT
  }
}

