package com.tarterware.roadrunner.configs;

import java.util.Arrays;
import java.util.List;

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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
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

    @Value("${com.tarterware.roadrunner.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception
    {
        http.cors(Customizer.withDefaults())
                // Configure CSRF protection for the SPA
                .csrf(csrf -> csrf
                        .csrfTokenRepository(
                                CookieCsrfTokenRepository.withHttpOnlyFalse())
                        /*
                         * Spring Security 6+ defaults to a deferred CSRF token. We force it to load on
                         * every request so the cookie is eagerly sent to the SPA without requiring a
                         * custom filter wrapper.
                         */
                        .csrfTokenRequestHandler(
                                new XorCsrfTokenRequestAttributeHandler()))
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
    JwtAuthenticationConverter jwtAuthenticationConverter()
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

        configuration.setAllowedOrigins(allowedOrigins);

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(Arrays.asList("Authorization"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}