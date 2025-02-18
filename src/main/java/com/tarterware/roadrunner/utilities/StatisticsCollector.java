package com.tarterware.roadrunner.utilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

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
 * StatisticsCollector collector = new StatisticsCollector(10, 250);
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

    /** Threshold value; values above this will not be considered in stats. */
    private double outlierThreshold;

    private DoubleSummaryStatistics summaryStatistics;

    /**
     * Constructs a {@code StatisticsCollector} with the specified capacity.
     * 
     * @param capacity the maximum number of measurements to retain; must be at
     *                 least 1
     * @param msPeriod the expected period of the task
     * @throws IllegalArgumentException if {@code capacity} is less than 1
     */
    public StatisticsCollector(int capacity, long msPeriod)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        if (msPeriod < 1)
        {
            throw new IllegalArgumentException("msPeriod must be at least 1");
        }
        this.capacity = capacity;
        this.executionTimes = new ArrayDeque<>(capacity);

        // Define the threshhold as 130% of the nominal period.
        this.outlierThreshold = (double) msPeriod * 1.3;
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

        // Sort execution times and determine the 95th percentile cutoff
        List<Double> sortedTimes = executionTimes.stream().sorted().collect(Collectors.toList());
        int cutoffIndex = (int) (sortedTimes.size() * 0.95); // Top 5% outliers

        // Use only values up to the 95th percentile
        List<Double> filteredTimes = sortedTimes.subList(0, cutoffIndex);

        // Compute the average of the filtered values
        summaryStatistics = filteredTimes.stream().mapToDouble(Double::doubleValue).summaryStatistics();
    }

    /**
     * Calculates and returns the average of the recorded execution times.
     *
     * @return the average execution time, or <code>outlierThreshold</code> if no
     *         measurements have been recorded
     */
    public double getAverageExecutionTime()
    {
        return summaryStatistics.getCount() > 0 ? summaryStatistics.getAverage() : outlierThreshold;
    }

    /**
     * Returns the minimum execution time among the recorded measurements.
     *
     * @return the minimum execution time, or 0.0 if no measurements have been
     *         recorded
     */
    public double getMinExecutionTime()
    {
        return summaryStatistics.getCount() > 0 ? summaryStatistics.getMin() : 0.0;
    }

    /**
     * Returns the maximum execution time among the recorded measurements.
     *
     * @return the maximum execution time, or <code>outlierThreshold</code> if no
     *         measurements have been recorded
     */
    public double getMaxExecutionTime()
    {
        return summaryStatistics.getCount() > 0 ? summaryStatistics.getMax() : outlierThreshold;
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
