package com.tarterware.roadrunner.utilities;

/**
 * A utility class for collecting jitter statistics (mean, standard deviation,
 * min, and max) over a fixed-size circular buffer.
 */
public class JitterStatisticsCollector
{

    private final int capacity;
    private final double[] measurements;
    private int index = 0;
    private int count = 0;

    // Recalculated statistics over the entire window.
    private double mean = 0.0;
    private double m2 = 0.0; // Variance times (n-1)

    private double min = Double.MAX_VALUE;
    private double max = Double.MIN_VALUE;

    public JitterStatisticsCollector(int capacity)
    {
        if (capacity < 1)
        {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.measurements = new double[capacity];
    }

    public JitterStatisticsCollector(JitterStatisticsCollector other, int newCapacity)
    {
        if (newCapacity < 1)
        {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }

        this.capacity = newCapacity;
        this.measurements = new double[newCapacity];
        int origStart = 0;
        if (newCapacity < other.capacity)
        {
            origStart = newCapacity - other.capacity;
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
     * @param value the jitter measurement in milliseconds
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
     */
    public synchronized double getMean()
    {
        return mean;
    }

    /**
     * Returns the standard deviation of the recorded measurements.
     */
    public synchronized double getStandardDeviation()
    {
        return count > 1 ? Math.sqrt(m2) : 0.0;
    }

    /**
     * Returns the minimum recorded measurement.
     */
    public synchronized double getMin()
    {
        return count > 0 ? min : 0.0;
    }

    /**
     * Returns the maximum recorded measurement.
     */
    public synchronized double getMax()
    {
        return count > 0 ? max : 0.0;
    }

    /**
     * Returns the current number of recorded measurements.
     */
    public synchronized int getCount()
    {
        return count;
    }
}
