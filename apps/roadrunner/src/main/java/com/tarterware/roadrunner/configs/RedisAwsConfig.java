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

@Configuration
@Profile("eks") // Only use in the "eks" profile
public class RedisAwsConfig
{
    @Value("${com.tarterware.redis.host}")
    private String _redisHost;

    @Value("${com.tarterware.redis.port}")
    private int _redisPort;

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
