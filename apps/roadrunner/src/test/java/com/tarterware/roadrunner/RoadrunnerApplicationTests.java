package com.tarterware.roadrunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;

@SpringBootTest
class RoadrunnerApplicationTests
{
    @MockBean
    private DirectionsService directionsService;
    
    @MockBean
    private GeocodingService geocodingService;
    
    @MockBean
    private SecurityConfig securityConfig;

    @MockBean
    private SecurityFilterChain filterChain;
    
    @MockBean
    private JwtDecoder jwtDecoder;
    
    @Test
    void contextLoads()
    {
    }
}
