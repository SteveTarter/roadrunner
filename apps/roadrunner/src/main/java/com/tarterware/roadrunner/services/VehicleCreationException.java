package com.tarterware.roadrunner.services;

/**
 * Exception thrown when a user attempts to exceed an allowed vehicle-start
 * limit.
 *
 * <p>
 * This exception is used by the vehicle usage-limit logic to stop requests that
 * would exceed the configured daily limit for a non-privileged user. It is
 * handled by the API exception handler and returned to the client as an HTTP
 * {@code 429 Too Many Requests} response.
 * </p>
 */
public class VehicleCreationException extends RuntimeException
{
    /**
     * Serialization identifier for this exception class.
     */
    private static final long serialVersionUID = -6922601752761751441L;

    /**
     * Creates a new exception with a message describing why the vehicle-start
     * request was rejected.
     *
     * @param message human-readable explanation of the limit violation
     */
    public VehicleCreationException(String message)
    {
        super(message);
    }
}