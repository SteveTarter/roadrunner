package com.tarterware.roadrunner.configs;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration class for setting up Redis connection in an AWS environment,
 * specifically tailored for the "eks" profile. This configuration uses IAM
 * authentication and SSL for secure communication with the Redis instance.
 */
@Configuration
@Profile("eks") // Only use in the "eks" profile
public class RedisAwsConfig
{
    @Value("${com.tarterware.redis.host}")
    private String _redisHost;

    @Value("${com.tarterware.redis.port}")
    private int _redisPort;

    /**
     * Configures a LettuceConnectionFactory for connecting to a standalone Redis
     * instance in AWS. This configuration includes setting up IAM authentication
     * and enabling SSL for secure communication.
     *
     * @return A LettuceConnectionFactory instance configured for standalone Redis
     *         connection with IAM and SSL.
     * @throws Exception If there is an issue generating the IAM token or
     *                   configuring the connection.
     */
    @Bean
    LettuceConnectionFactory redisStandAloneConnectionFactory() throws Exception
    {
        // Generate the IAM token
        RedisIAMTokenGenerator tokenGenerator = new RedisIAMTokenGenerator();
        String iamAuthToken = tokenGenerator.generateIAMAuthToken(_redisHost, _redisPort);

        // Configure Redis client with IAM token
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(_redisHost);
        redisConfig.setPort(_redisPort);
        redisConfig.setPassword(iamAuthToken); // Pass the IAM token as the Redis password

        // Enable SSL using LettuceClientConfiguration
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder().useSsl() // Enable SSL
                .and().commandTimeout(Duration.ofSeconds(10)) // Optional: Configure timeout
                .build();

        // Combine Redis configuration and Lettuce client configuration
        return new LettuceConnectionFactory(redisConfig, clientConfig);
    }

    /**
     * Configures the serializer for values in Redis to use
     * GenericJackson2JsonRedisSerializer, which allows storing objects as JSON.
     */
    @Bean
    RedisTemplate<String, Object> redisTemplateStandAlone(
            @Qualifier("redisStandAloneConnectionFactory") LettuceConnectionFactory redisConnectionFactory)
    {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Use StringRedisSerializer for keys
        template.setKeySerializer(new StringRedisSerializer());

        // Use GenericJackson2JsonRedisSerializer for values
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
