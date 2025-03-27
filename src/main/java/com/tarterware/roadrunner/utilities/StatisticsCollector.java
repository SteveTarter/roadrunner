package com.tarterware.roadrunner.utilities;

/**
 * A utility class for collecting statistics (mean, standard deviation, min, and
 * max) over a fixed-size circular buffer.
 */
public class StatisticsCollector
{
    // Maximum number of measurements to retain
    private final int capacity;

    // Circular buffer to hold measurements
    private final double[] measurements;

    // Current position in the circular buffer
    private int index = 0;

    // Current number of valid measurements in the buffer
    private int count = 0;

    // Recalculated statistics over the entire window
    private double mean = 0.0;
    private double m2 = 0.0; // Variance times (n-1)

    // Minimum and maximum measurements observed
    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;

    /**
     * Constructs a new StatisticsCollector with the specified capacity.
     *
     * @param capacity the maximum number of measurements to retain
     * @throws IllegalArgumentException if capacity is less than 1
     */
    public StatisticsCollector(int capacity)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.measurements = new double[capacity];
    }

    /**
     * Constructs a new StatisticsCollector using data from another collector, but
     * with a new buffer capacity. Copies the most recent measurements up to the new
     * capacity and recalculates statistics.
     *
     * @param other       the original StatisticsCollector to copy from
     * @param newCapacity the capacity of the new collector
     * @throws IllegalArgumentException if newCapacity is less than 1
     */
    public StatisticsCollector(StatisticsCollector other, int newCapacity)
    {
        if (newCapacity < 1)
        {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }

        this.capacity = newCapacity;
        this.measurements = new double[newCapacity];

        // If the new buffer is smaller, skip older values.
        int origStart = 0;
        if (newCapacity < other.capacity)
        {
            origStart = other.capacity - newCapacity;
        }

        int length = Math.min(newCapacity, other.capacity);
        System.arraycopy(other.measurements, origStart, measurements, 0, length);
        count = length;
        index = length - 1;

        // Recalculate all statistics over the current window.
        recalcAll();
    }

    /**
     * Records a new measurement. If the collector is full, it replaces the oldest
     * value and then recalculates statistics over the entire window.
     *
     * @param value the measurement
     */
    public synchronized void recordMeasurement(double value)
    {
        // Write the new measurement into the circular buffer.
        measurements[index] = value;
        index = (index + 1) % capacity;
        if (count < capacity)
        {
            count++;
        }

        // Recalculate all statistics over the current window.
        recalcAll();
    }

    /**
     * Recalculates mean, standard deviation, min, and max values from the current
     * measurements in the buffer.
     */
    private void recalcAll()
    {
        double sum = 0.0;
        double sumSq = 0.0;
        double currentMin = Double.MAX_VALUE;
        double currentMax = Double.MIN_VALUE;

        // When the buffer isn't full, only consider the first 'count' elements;
        // when full, all 'capacity' elements are valid.
        int n = (count < capacity) ? count : capacity;

        for (int i = 0; i < n; i++)
        {
            double v = measurements[i];
            sum += v;
            sumSq += v * v;
            if (v < currentMin)
            {
                currentMin = v;
            }
            if (v > currentMax)
            {
                currentMax = v;
            }
        }

        mean = (n > 0) ? sum / n : 0.0;

        // Compute sample variance (m2 is variance*(n-1))
        if (n > 1)
        {
            double variance = (sumSq - n * mean * mean) / (n - 1);
            m2 = variance;
        }
        else
        {
            m2 = 0.0;
        }

        min = currentMin;
        max = currentMax;
    }

    /**
     * Returns the average of the recorded measurements.
     *
     * @return the mean value
     */
    public synchronized double getMean()
    {
        return mean;
    }

    /**
     * Returns the standard deviation of the recorded measurements.
     *
     * @return the standard deviation, or 0 if fewer than two values are recorded
     */
    public synchronized double getStandardDeviation()
    {
        return count > 1 ? Math.sqrt(m2) : 0.0;
    }

    /**
     * Returns the minimum recorded measurement.
     *
     * @return the minimum value, or 0 if no measurements are recorded
     */
    public synchronized double getMin()
    {
        return count > 0 ? min : 0.0;
    }

    /**
     * Returns the maximum recorded measurement.
     *
     * @return the maximum value, or 0 if no measurements are recorded
     */
    public synchronized double getMax()
    {
        return count > 0 ? max : 0.0;
    }

    /**
     * Returns the current number of recorded measurements.
     *
     * @return the number of values currently in the buffer
     */
    public synchronized int getCount()
    {
        return count;
    }
}
