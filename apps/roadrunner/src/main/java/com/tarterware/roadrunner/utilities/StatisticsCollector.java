package com.tarterware.roadrunner.utilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalDouble;

/**
 * <p>
 * A utility class for collecting and maintaining a fixed-size collection of
 * double measurements (such as execution times). When the number of recorded
 * values reaches the specified capacity, the oldest value is automatically
 * removed to make room for a new one.
 * </p>
 * 
 * <p>
 * Example usage:
 * 
 * <pre>
 * // Create a StatisticsCollector that retains the last 10 execution times.
 * StatisticsCollector collector = new StatisticsCollector(10);
 * 
 * // Record a new execution time measurement.
 * collector.recordExecutionTime(123.4);
 * 
 * // Get aggregate statistics.
 * double average = collector.getAverageExecutionTime();
 * double max = collector.getMaxExecutionTime();
 * </pre>
 * </p>
 */
public class StatisticsCollector
{

    /** A fixed-size queue to hold the recorded measurements. */
    private final Deque<Double> executionTimes;

    /** The maximum number of measurements to retain. */
    private final int capacity;

    /**
     * Constructs a {@code StatisticsCollector} with the specified capacity.
     * 
     * @param capacity the maximum number of measurements to retain; must be at
     *                 least 1
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public StatisticsCollector(int capacity)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.executionTimes = new ArrayDeque<>(capacity);
    }

    /**
     * Records a new execution time measurement. If the collector has reached its
     * capacity, the oldest measurement is automatically removed.
     *
     * @param ms the execution time in milliseconds
     */
    public void recordExecutionTime(double ms)
    {
        if (executionTimes.size() == capacity)
        {
            // Remove the oldest measurement to maintain the fixed capacity.
            executionTimes.poll();
        }
        executionTimes.offer(ms);
    }

    /**
     * Calculates and returns the average of the recorded execution times.
     *
     * @return the average execution time, or 0.0 if no measurements have been
     *         recorded
     */
    public double getAverageExecutionTime()
    {
        OptionalDouble average = executionTimes.stream().mapToDouble(Double::doubleValue).average();
        return average.orElse(0.0);
    }

    /**
     * Returns the minimum execution time among the recorded measurements.
     *
     * @return the minimum execution time, or 0.0 if no measurements have been
     *         recorded
     */
    public double getMinExecutionTime()
    {
        OptionalDouble min = executionTimes.stream().mapToDouble(Double::doubleValue).min();
        return min.orElse(0.0);
    }

    /**
     * Returns the maximum execution time among the recorded measurements.
     *
     * @return the maximum execution time, or 0.0 if no measurements have been
     *         recorded
     */
    public double getMaxExecutionTime()
    {
        OptionalDouble max = executionTimes.stream().mapToDouble(Double::doubleValue).max();
        return max.orElse(0.0);
    }

    /**
     * Returns the current number of recorded measurements.
     *
     * @return the number of recorded execution times
     */
    public int getCount()
    {
        return executionTimes.size();
    }

    /**
     * Returns the capacity of this collector.
     *
     * @return the maximum number of execution times this collector can store
     */
    public int getCapacity()
    {
        return capacity;
    }
}
