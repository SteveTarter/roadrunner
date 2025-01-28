package com.tarterware.roadrunner;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.tarterware.roadrunner.configs.RedisConfig;
import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;

@SpringBootTest
@ActiveProfiles("test")
class RoadrunnerApplicationTests
{
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

    @BeforeEach
    void setup() throws IOException
    {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void contextLoads()
    {
    }
}
