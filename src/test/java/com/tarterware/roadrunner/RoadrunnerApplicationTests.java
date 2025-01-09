package com.tarterware.roadrunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.tarterware.roadrunner.configs.SecurityConfig;
import com.tarterware.roadrunner.services.DirectionsService;

@SpringBootTest
@ImportAutoConfiguration(exclude = { SecurityConfig.class })
class RoadrunnerApplicationTests
{
    @MockBean
    private DirectionsService directionsService;
    
    @Test
    void contextLoads()
    {
    }
}
