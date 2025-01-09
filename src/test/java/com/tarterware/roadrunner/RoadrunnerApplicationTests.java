package com.tarterware.roadrunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.services.DirectionsService;
import com.tarterware.roadrunner.services.GeocodingService;
import com.tarterware.roadrunner.services.IsochroneService;

@SpringBootTest
class RoadrunnerApplicationTests
{
    @MockBean
    private DirectionsService directionsService;
    
    @MockBean
    private GeocodingService geocodingService;
    
    @MockBean
    private IsochroneService isochroneService;
    
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
