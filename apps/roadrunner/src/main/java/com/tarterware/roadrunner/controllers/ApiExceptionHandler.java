package com.tarterware.roadrunner.controllers;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.tarterware.roadrunner.services.VehicleLimitExceededException;

/**
 * Centralized exception handler for Roadrunner REST controllers.
 *
 * <p>
 * This advice converts application-specific exceptions into structured HTTP
 * responses that can be consumed by the frontend. Keeping this behavior in one
 * place avoids duplicating error handling logic across individual controllers.
 * </p>
 */
@RestControllerAdvice
public class ApiExceptionHandler
{
    /**
     * Handles requests that exceed the allowed daily vehicle-start limit.
     *
     * <p>
     * The response uses HTTP {@code 429 Too Many Requests} and includes a small
     * JSON body containing the exception message, HTTP status code, and response
     * timestamp. The frontend can read the {@code message} field and display it to
     * the user.
     * </p>
     *
     * @param ex the exception thrown when the authenticated user has exceeded the
     *           allowed vehicle-start limit
     * @return a structured error response for the failed request
     */
    @ExceptionHandler(VehicleLimitExceededException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ErrorResponse handleVehicleLimitExceeded(VehicleLimitExceededException ex)
    {
        return new ErrorResponse(
                ex.getMessage(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                Instant.now());
    }

    /**
     * JSON response body returned for handled API errors.
     *
     * @param message   human-readable error message suitable for display in the
     *                  frontend
     * @param status    HTTP status code associated with the error
     * @param timestamp time at which the error response was created
     */
    public record ErrorResponse(
            String message,
            int status,
            Instant timestamp
    )
    {
    }
}