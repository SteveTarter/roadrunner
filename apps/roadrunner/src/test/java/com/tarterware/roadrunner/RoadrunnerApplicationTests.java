package com.tarterware.roadrunner;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.tarterware.roadrunner.configs.NoOpSchedulerConfig;
import com.tarterware.roadrunner.configs.RedisConfig;
import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;
import com.tarterware.roadrunner.services.KafkaTopicMetadataService;

import io.fabric8.kubernetes.client.KubernetesClient;

@SpringBootTest
@Import(NoOpSchedulerConfig.class)
@Testcontainers
class RoadrunnerApplicationTests
{
    @SuppressWarnings("resource")
    @Container
    public static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry)
    {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }

    @MockitoBean
    private DirectionsService directionsService;

    @MockitoBean
    private GeocodingService geocodingService;

    @MockitoBean
    private IsochroneService isochroneService;

    @MockitoBean
    private SecurityConfig securityConfig;

    @MockitoBean
    private RedisConfig redisConfig;

    @MockitoBean
    private LettuceConnectionFactory redisStandAloneConnectionFactory;

    @MockitoBean
    private SecurityFilterChain filterChain;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockitoBean
    private KubernetesClient kubernetesClient;

    @MockitoBean
    private KafkaTopicMetadataService kafkaTopicMetadataService;

    @BeforeEach
    void setup() throws IOException
    {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);
        when(kafkaTopicMetadataService.getTopicRetention(anyString())).thenReturn(Duration.ofDays(7));
    }

    @Test
    void contextLoads()
    {
    }
}
