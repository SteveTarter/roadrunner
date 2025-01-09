package com.tarterware.roadrunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.services.DirectionsService;

@SpringBootTest
class RoadrunnerApplicationTests
{
    @MockBean
    private DirectionsService directionsService;
    
    @MockBean
    private SecurityConfig securityConfig;

    @Test
    void contextLoads()
    {
    }
}
