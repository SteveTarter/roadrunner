package com.tarterware.roadrunner.configs;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Profile("!eks") // Dev and minikube use a stock redis stack, while eks uses memorydb
public class RedisConfig
{
    @Value("${com.tarterware.redis.host}")
    private String _redisHost;

    @Value("${com.tarterware.redis.port}")
    private int _redisPort;

    @Value("${com.tarterware.redis.password}")
    private String redisPassword;

    @Bean
    LettuceConnectionFactory redisStandAloneConnectionFactory()
    {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(_redisHost, _redisPort);
        configuration.setPassword(redisPassword); // Set the password here
        return new LettuceConnectionFactory(configuration);
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
