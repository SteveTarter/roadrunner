package com.tarterware.roadrunner.configs;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides time-related application configuration.
 *
 * <p>
 * This configuration exposes a {@link Clock} bean so application services can
 * depend on an injectable time source instead of calling static time methods
 * directly. Using an injected clock keeps production code consistent and makes
 * time-dependent behavior easier to test with a fixed or mock clock.
 * </p>
 */
@Configuration
public class TimeConfig
{
    /**
     * Creates the application-wide clock used by services that need the current
     * date or time.
     *
     * <p>
     * The clock uses UTC to avoid behavior that depends on the server's local time
     * zone. This is useful for features such as daily usage limits, expiring
     * counters, and other date-based calculations.
     * </p>
     *
     * @return a UTC system clock
     */
    @Bean
    Clock clock()
    {
        return Clock.systemUTC();
    }
}