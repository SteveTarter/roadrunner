package com.tarterware.roadrunner.services;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.tarterware.roadrunner.security.UserPrincipal;

/**
 * Enforces per-user vehicle-start limits for the Roadrunner demo.
 *
 * <p>
 * This service uses Redis to maintain a daily vehicle-start counter for each
 * authenticated user. The counter is keyed by the user's stable subject
 * identifier and the current UTC date. Redis is used so increments are atomic
 * and safe across concurrent requests or multiple application instances.
 * </p>
 *
 * <p>
 * Users in the {@code superuser} group are exempt from the configured daily
 * limit. All other authenticated users are limited by
 * {@code com.tarterware.roadrunner.usage-limits.default-daily-vehicle-starts},
 * which defaults to {@code 30}.
 * </p>
 */
@Service
public class VehicleUsageService
{
    private static final Logger log = LoggerFactory.getLogger(VehicleUsageService.class);

    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Value("${com.tarterware.roadrunner.usage-limits.default-daily-vehicle-starts:30}")
    private int defaultDailyVehicleStarts;

    @Value("${com.tarterware.roadrunner.usage-limits.redis-key-prefix:roadrunner:usage:vehicle-starts}")
    private String redisKeyPrefix;

    @Value("${com.tarterware.roadrunner.usage-limits.counter-ttl-hours:48}")
    private long counterTtlHours;

    /**
     * Creates a vehicle usage service backed by Redis.
     *
     * <p>
     * The {@link Clock} dependency is injected to keep date-based behavior
     * testable. Production configuration should normally provide a UTC system
     * clock.
     * </p>
     *
     * @param redisTemplate Redis template used to read, increment, and expire usage
     *                      counters
     * @param clock         time source used to determine the current UTC usage date
     */
    VehicleUsageService(StringRedisTemplate redisTemplate, Clock clock)
    {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    /**
     * Checks whether the user may start another vehicle and reserves one vehicle
     * start if allowed.
     *
     * <p>
     * This method should be called immediately before creating or starting a
     * vehicle. For non-superuser accounts, it increments the user's Redis counter
     * for the current UTC day. If the incremented value exceeds the configured
     * daily limit, the request is rejected by throwing a
     * {@link VehicleLimitExceededException}.
     * </p>
     *
     * <p>
     * The Redis increment operation is atomic, so concurrent requests from the same
     * user cannot bypass the limit by reading the same stale count.
     * </p>
     *
     * @param user authenticated user principal for the request
     * @throws VehicleLimitExceededException if the user cannot be identified or has
     *                                       exceeded the daily vehicle-start limit
     * @throws IllegalStateException         if Redis does not return a counter
     *                                       value after incrementing
     */
    public void assertCanStartVehicles(UserPrincipal user)
    {
        if (user == null || user.sub() == null || user.sub().isBlank())
        {
            throw new VehicleLimitExceededException("Unable to determine authenticated user.");
        }

        if (user.isSuperuser())
        {
            return;
        }

        String key = dailyUsageKey(user.sub());

        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null)
        {
            throw new IllegalStateException("Redis did not return a usage count.");
        }

        if (count == 1L)
        {
            redisTemplate.expire(key, Duration.ofHours(counterTtlHours));
        }

        if (count > defaultDailyVehicleStarts)
        {
            log.warn(
                    "Vehicle start rejected for userSub={} count={} limit={}",
                    user.sub(),
                    count,
                    defaultDailyVehicleStarts);

            throw new VehicleLimitExceededException(
                    "Daily vehicle limit reached. Please try again tomorrow.");
        }

        log.debug(
                "Vehicle start accepted for userSub={} count={} limit={}",
                user.sub(),
                count,
                defaultDailyVehicleStarts);
    }

    /**
     * Returns the number of vehicle starts currently recorded for the user on the
     * current UTC date.
     *
     * <p>
     * This method does not increment or reserve usage. It is intended for display,
     * diagnostics, or status endpoints that need to show the user's current usage.
     * If no Redis counter exists for the user and date, the method returns
     * {@code 0}.
     * </p>
     *
     * @param user authenticated user principal
     * @return current daily vehicle-start count, or {@code 0} if no counter exists
     *         or the user cannot be identified
     */
    public int getVehiclesStartedToday(UserPrincipal user)
    {
        if (user == null || user.sub() == null || user.sub().isBlank())
        {
            return 0;
        }

        String value = redisTemplate.opsForValue().get(dailyUsageKey(user.sub()));

        if (value == null)
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e)
        {
            log.warn("Invalid vehicle usage counter value for userSub={}: {}", user.sub(), value);
            return 0;
        }
    }

    /**
     * Builds the Redis key used to store the user's daily vehicle-start count.
     *
     * <p>
     * The key includes the configured prefix, the user's Cognito subject
     * identifier, and the current UTC date. Including the date creates a separate
     * counter for each day while allowing old counters to expire naturally.
     * </p>
     *
     * @param userSub stable subject identifier for the authenticated user
     * @return Redis key for the user's current daily usage counter
     */
    private String dailyUsageKey(String userSub)
    {
        LocalDate todayUtc = LocalDate.now(clock.withZone(ZoneOffset.UTC));

        return String.format(
                "%s:%s:%s",
                redisKeyPrefix,
                userSub,
                todayUtc);
    }
}