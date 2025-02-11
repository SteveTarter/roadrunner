package com.tarterware.roadrunner.services;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.Data;
import lombok.NoArgsConstructor;

@Service
@Profile("!dev") // iDo not use in the "dev" profile
public class PrometheusTokenUpdater
{
    // Inject Auth0 and endpoint properties
    @Value("${auth0.api.audience:}")
    private String auth0ApiAudience;

    @Value("${auth0.api.client-id:}")
    private String auth0ApiClientId;

    @Value("${auth0.api.client-secret:}")
    private String auth0ApiClientSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}")
    private String tokenEndpointUrl;

    // Kubernetes secret configuration
    @Value("${prometheus.secret.namespace:}")
    private String secretNamespace;

    @Value("${prometheus.secret.name:}")
    private String secretName;

    private final KubernetesClient kubernetesClient;
    RestTemplate restTemplate;

    private static final Logger logger = LoggerFactory.getLogger(PrometheusTokenUpdater.class);

    public PrometheusTokenUpdater(KubernetesClient kubernetesClient, RestTemplate restTemplate)
    {
        this.kubernetesClient = kubernetesClient;
        this.restTemplate = restTemplate;
    }

    // Run once a day (86400000 milliseconds)
    @Scheduled(fixedRate = 86400000)
    public void updatePrometheusToken()
    {
        try
        {
            StringBuilder sb = new StringBuilder(tokenEndpointUrl);
            sb.append("oauth/token");

            // Prepare the request payload for your token endpoint.
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("audience", auth0ApiAudience);
            requestBody.put("client_id", auth0ApiClientId);
            requestBody.put("client_secret", auth0ApiClientSecret);
            requestBody.put("grant_type", "client_credentials");

            // Call the token endpoint and retrieve the token.
            // Adjust the JSON field name ("token") if your API response is different.
            ResponseEntity<TokenResponse> respTokenResponse = restTemplate.postForEntity(sb.toString(), requestBody,
                    TokenResponse.class);

            if (respTokenResponse == null)
            {
                throw new RuntimeException("Failed to retrieve a valid token from the endpoint.");
            }

            String token = respTokenResponse.getBody().getAccessToken();
            String tokenEncoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));

            // Fetch the existing secret, if any.
            Secret secret = kubernetesClient.secrets().inNamespace(secretNamespace).withName(secretName).get();

            if (secret == null)
            {
                // Create a new secret if it doesn't exist.
                Secret newSecret = new SecretBuilder().withNewMetadata().withName(secretName)
                        .withNamespace(secretNamespace).endMetadata().addToData("token", tokenEncoded).build();
                kubernetesClient.secrets().inNamespace(secretNamespace).resource(newSecret).create();
            }
            else
            {
                // Update the existing secret.
                secret.getData().put("token", tokenEncoded);
                kubernetesClient.secrets().inNamespace(secretNamespace).resource(secret).update();
            }

            logger.info("Prometheus token updated successfully.");
        }
        catch (Exception e)
        {
            // Log error or handle accordingly.
            logger.error("Error updating Prometheus token", e);
        }
    }

    @NoArgsConstructor
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenResponse
    {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("expires_in")
        private int expiresIn;

        @JsonProperty("token_type")
        private String tokenType;
    }
}
