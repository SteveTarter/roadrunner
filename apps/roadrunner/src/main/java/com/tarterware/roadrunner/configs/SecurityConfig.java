package com.tarterware.roadrunner.configs;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@Profile("!test") // Don't create in test profile
public class SecurityConfig
{
    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String jwtIssuerUri;

    @Value("${cognito.app-client-id}")
    private String cognitoAppClientId;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorizeRequests -> authorizeRequests
                        // Publicly accessible endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Protect sensitive metrics with RBAC
                        .requestMatchers("/actuator/metrics/**", "/actuator/prometheus/**")
                        .hasAuthority("ROLE_superuser")
                        // Allow unauthenticated access to actuator health and info endpoints
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // All other requests require standard authentication
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter()
    {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();

        // Tell Spring to look at 'cognito:groups' instead of 'scope'
        authoritiesConverter.setAuthoritiesClaimName("cognito:groups");

        // Prefix the groups with 'ROLE_' so .hasRole("ADMIN") works
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder()
    {
        // Builds decoder from issuer metadata (jwks_uri etc.)
        NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(jwtIssuerUri);

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(jwtIssuerUri);

        // Ensure this is an ACCESS token, not an ID token
        OAuth2TokenValidator<Jwt> tokenUseValidator = jwt ->
        {
            String tokenUse = jwt.getClaimAsString("token_use");
            if (!"access".equals(tokenUse))
            {
                return OAuth2TokenValidatorResult
                        .failure(new OAuth2Error("invalid_token", "token_use must be 'access'", null));
            }
            return OAuth2TokenValidatorResult.success();
        };

        // Ensure token was minted for your SPA app client
        OAuth2TokenValidator<Jwt> clientIdValidator = jwt ->
        {
            String clientId = jwt.getClaimAsString("client_id");
            if (clientId == null || !cognitoAppClientId.equals(clientId))
            {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "client_id mismatch", null));
            }
            return OAuth2TokenValidatorResult.success();
        };

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerValidator, tokenUseValidator, clientIdValidator));
        return decoder;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource()
    {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000",
                "https://roadrunner-view.tarterware.info", "https://roadrunner-view.tarterware.com"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        // If you ever send cookies cross-site, you’d need:
        // configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}